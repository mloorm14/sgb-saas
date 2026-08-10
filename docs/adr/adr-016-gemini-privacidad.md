# ADR-016: Datos enviados a Gemini y por qué no es exposición indebida

## Estado

Aceptado

## Contexto

El Módulo H (chatbot asistente virtual) integra la API pública `generateContent`
de Google Gemini. Por ser una API **externa al sistema**, cada mensaje del
lector viaja al servidor de Google, y es obligatorio documentar qué datos van
exactamente y justificar por qué eso no representa una exposición indebida de
información (cultura del repo: toda decisión de este tipo queda registrada,
ver los ADR de OWASP A02/A05 y las mediciones en `docs/mediciones/sec/`).

Los datos que `GeminiClient` envía a la API en cada llamada son:

1. **El texto del mensaje del usuario** (`dto.texto()`), limitado a 500
   caracteres por validación (`MensajeChatRequestDTO`).
2. **El historial de la sesión** (`MensajeChatRepository
   .findBySesionIdOrderByCreadoEnAsc`), mapeado a roles `user`/`model`.
3. **El prompt de sistema construido por `ChatbotService`**, que incluye:
   - las entradas activas de `base_conocimiento` (preguntas/respuestas
     curadas por el equipo, publicadas en la migración V9);
   - los resultados reales de `LibroService.sugerir(texto)` (id, título y
     disponibilidad derivada de stock, ya expuestos públicamente por
     `GET /api/v1/libros/sugerencias` para cualquier usuario autenticado)
     **solo cuando** el texto contiene indicios de consulta de disponibilidad;
   - instrucciones de no-ejecución si el texto sugiere una reserva.

**Nunca** se envía: credenciales, tokens JWT, contraseñas, identificación
personal (cédula), correo del usuario, estado de la cuenta ni ningún dato de
tablas ajenas al chat. `SesionChat`/`MensajeChat` solo guardan `usuarioId`
(clave foránea numérica), rol y contenido del mensaje.

## Decisión

Se acepta el envío de los 3 conjuntos de datos listados a Gemini, con estas
salvaguardas, y **no** se envía nada más:

- **Mínimo necesario**: el prompt de sistema instruye al modelo a responder
  SOLO con el contexto real provisto y a nunca inventar disponibilidad
  (comentario explícito en `GeminiClient`); sin el historial y el grounding,
  el asistente no podría mantener una conversación coherente ni responder
  sobre disponibilidad real.
- **El contenido del chat es la entrada del propio usuario**: lo que se
  reenvía a Gemini es lo que el lector escribió y la respuesta del sistema —
  no es información de terceros ni registros internos del sistema.
- **Grounding con datos ya públicos**: `base_conocimiento` (migración V9,
  seed del repo) y `LibroService.sugerir` (endpoint autenticado pero abierto
  a cualquier rol, datos del catálogo) no contienen datos personales.
- **El API key nunca viaja en el cuerpo ni en headers de la app**: va como
  query param `?key=` del request `GeminiClient` hacia Google, configurada
  por variable de entorno (`app.gemini.api-key` / `GEMINI_API_KEY`), nunca
  commitada en claro (ver `.env.example`).

### Decisiones técnicas asociadas

- **HTTP directo vía `RestClient`, sin SDK de Vertex AI**: el roadmap lo
  dejaba como opción, pero se prioriza no agregar una dependencia nueva
  (spring-web trae `RestClient`) y tener control explícito de timeouts y
  manejo de errores (429/timeout → mensaje amigable, nunca error crudo).
- **Prompt de sistema vía campo `systemInstruction`** (no como primer
  mensaje): deja el arreglo `contents` limpio para alternar roles
  user/model como exige la API.
- **Reservas desde el chat diferidas a v2**: `ReservacionService` se inyecta
  como punto de integración, pero `crear()` no se ejecuta desde el chat en
  esta versión — el modelo no puede mapear de forma confiable un título a un
  `libroId` sin riesgo de inventarlo (documentado en `ChatbotService`).

## Consecuencias

- El equipo debe tratar `GEMINI_API_KEY` como secreto (`.env`, secret de CI),
  y saber que Gemini recibe el texto del chat del lector: esto debe
  mencionarse en el aviso de privacidad del sistema si llega a producción.
- El contenido del chat queda persistido en `mensajes_chat` (migración V9)
  con el mismo criterio de retención que el resto de tablas de la BD; no se
  agregó retención/borrado especial en esta versión (riesgo aceptado y
  documentado, igual que otras decisiones de retención del repo).
- Si en el futuro se decide ejecutar reservas desde el chat (v2), el
  grounding deberá extenderse para seleccionar el libro por `libroId`
  explícito con confirmación del usuario, y este ADR debe revisitarse para
  confirmar que el `libroId` del catálogo tampoco es dato sensible.
- La API externa introduce una dependencia de disponibilidad: un fallo o 429
  de Gemini nunca rompe el flujo (el cliente devuelve un mensaje amigable que
  se persiste como respuesta del asistente), pero el chatbot queda sin
  responder durante la ventana del fallo.
