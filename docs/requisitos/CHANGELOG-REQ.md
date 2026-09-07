# Changelog de requisitos — SGB-SaaS

Bitácora de cambios de requisitos entre la Tercera Entrega
([`SRS-v0.9.0-rc.md`](historico/SRS-v0.9.0-rc.md), 30 requisitos) y la
Entrega Final ([`SRS-v1.0.0.md`](SRS-v1.0.0.md), 43 requisitos).

## Procedencia y método

Este documento se generó comparando el **cuerpo completo** de cada
requisito compartido entre ambas versiones (no solo el título) — extraído
programáticamente de los encabezados `#### REQ-F-XXX — título` /
`##### REQ-NF-XXX — título` de ambos archivos `.md`. Dos IDs
(`REQ-F-016`, `REQ-NF-009`) aparecieron inicialmente como "modificados" en
una primera pasada del script de comparación, pero al inspeccionar el
diff real resultó ser un artefacto del propio proceso de extracción (un
separador `---` de Markdown quedaba capturado como parte del cuerpo del
requisito cuando este quedaba justo antes de un salto de sección, algo que
cambió solo porque se insertaron requisitos nuevos después de ellos, no
porque su contenido cambiara) — **se descartan de la lista de
modificados** tras confirmar que su cuerpo real (Prioridad, Fuente,
Descripción, Rationale, Criterios de aceptación, Método de verificación)
es byte a byte idéntico entre ambas versiones. Se documenta este
descarte aquí en vez de contar una modificación que en realidad no
ocurrió.

## Métricas

| Métrica | Valor |
|---|---|
| **Número total de requisitos (v1.0.0)** | **43** |
| **Distribución por tipo** | **28 funcionales** (`REQ-F`, 65.1%) / **15 no funcionales** (`REQ-NF`, 34.9%) |
| **Porcentaje verificado** | **21 / 43 = 48.8%** con `estado = verificado` en `docs/trazabilidad/matriz.csv` (los 22 restantes, 51.2%, tienen `estado = implementado` — código real y, en la mayoría de los casos, con prueba automatizada, pero sin el nivel adicional de evidencia empírica en vivo contra el stack real que distingue a "verificado" en la convención de esta matriz; ningún requisito tiene otro estado) |
| **Tasa de estabilidad** | **1 − (2 / 43) = 0.9535 ≈ 95.3%** (2 requisitos modificados sobre 43 totales en v1.0.0) |

## Tabla de cambios

| ID | Tipo de cambio | Descripción breve | Commit |
|---|---|---|---|
| REQ-F-017 | Agregado | Configuración paramétrica del sistema (`ConfiguracionSistemaController`/`Service`) | `4b5d09b` |
| REQ-F-018 | Agregado | Renovación de préstamo, con 3 controles de negocio (vencido, límite de renovaciones, reserva de otro usuario) | `4b5d09b` |
| REQ-F-019 | Agregado | Credencial QR: consulta propia y registro de préstamo identificando al lector por QR | `4b5d09b` |
| REQ-F-020 | Agregado | Verificación de correo tras el registro (código OTP en Redis, TTL configurable) | `4b5d09b` |
| REQ-F-021 | Agregado | Consulta de notificaciones propias | `4b5d09b` |
| REQ-F-022 | Agregado | Generación automática de alertas (vencimiento, multa, reserva caducada) | `4b5d09b` |
| REQ-F-023 | Agregado | Administración de usuarios (cambio de rol/estado, listado del padrón) | `4b5d09b` |
| REQ-F-024 | Agregado | Consulta de la bitácora de auditoría (`GERENTE`/`ADMIN`) | `4b5d09b` |
| REQ-F-025 | Agregado | Reporte de índice de morosidad | `4b5d09b` |
| REQ-F-026 | Agregado | Reporte de uso por período | `4b5d09b` |
| REQ-F-027 | Agregado | Exportación a PDF del reporte de morosidad | `4b5d09b` |
| REQ-F-028 | Agregado | Asistente virtual (Chatbot) con Gemini, grounding real sobre catálogo/reservas | `4b5d09b` |
| REQ-NF-015 | Agregado | Automatización de CI/CD (`ci.yml`) y documentación de API (`OpenApiConfig`), más `make bench`/`make audit` reales | `4b5d09b` |
| REQ-NF-012 | Modificado | Estado cambia de "pendiente" a "parcialmente implementado": decisión de arquitectura (ADR-015) y preparación del backend (`forward-headers-strategy`) cerradas; TLS real end-to-end sigue sin implementar, declarado sin ambigüedad | `4b5d09b` |
| REQ-NF-014 | Modificado | Estado cambia de "pendiente" a "implementado del lado backend": CSP/stacktraces/Swagger-en-prod/usuario-no-root verificados contra Docker real (incluyendo el fix de `NoResourceFoundException`, commit `951fae5`); CSP de `nginx.conf` en el frontend sigue pendiente | `4b5d09b` |
| — | Eliminado | Ninguno — los 30 requisitos de v0.9.0-rc siguen presentes en v1.0.0, confirmado por comparación exhaustiva de IDs, no asumido | — |

## Correcciones post-auditoría del Dr. Gleiston Guerrero (2026-09-07)

El Dr. Guerrero (docente, ADB) auditó `SRS-v1.0.0.pdf` (enviado 2026-09-05)
contra ISO/IEC/IEEE 29148:2018 y contra el repositorio real en el tag
`v1.0.0`. Su hallazgo central: el documento entonces vigente documentaba 43
requisitos, pero la matriz real (`docs/trazabilidad/matriz.csv`) ya tenía 46
filas y el código real 31 controladores/143 rutas, frente a los 15/44 que
citaba la sección 3.3 — "la mayoría son requisitos faltantes de REDACCIÓN,
no desarrollo pendiente". Las siguientes entradas documentan las
correcciones aplicadas en respuesta, verificadas contra el código real en
este mismo commit (no contra suposiciones):

| ID | Tipo de cambio | Descripción breve | Fecha |
|---|---|---|---|
| REQ-F-001 | Modificado (contradicción interna corregida) | El criterio 1 y la descripción decían que el usuario queda `ACTIVO` tras el registro; `AuthService.java:43` (`ESTADO_INICIAL = "PENDIENTE_VERIFICACION"`) y el test `registroExitoso_dejaAlUsuarioPendienteDeVerificacionYEnviaElCodigo` confirman que el estado real es `PENDIENTE_VERIFICACION`. Se corrige el requisito y se agrega el campo "Depende de: REQ-F-020". | 2026-09-07 |
| REQ-F-020 | Modificado (prioridad) | Prioridad corregida de `Should` a `Must`: REQ-F-002 (Must) depende del estado `PENDIENTE_VERIFICACION` que solo existe porque REQ-F-020 lo introduce; un requisito Must no puede depender funcionalmente de uno Should. Actualizado también en `matriz.csv`. | 2026-09-07 |
| REQ-F-005 | Modificado (alcance acotado) | Se aclara explícitamente que este requisito aplica solo al catálogo autenticado (`GET /api/v1/libros`); el portal público sin cuenta (`/api/publico/**`, `permitAll` en `SecurityConfig.java`) es un requisito distinto, ver A6. | 2026-09-07 |
| — | Nota (sin cambio de ID todavía) | Secciones 1.2 y 2.2 ya mencionaban favoritos/sugerencias de adquisición sin requisito `REQ-F-XXX` formal; se agrega nota de referencia a A4/A5 (redactados en el Bloque 5 de esta misma tarea de corrección). | 2026-09-07 |
| REQ-F-008 | Modificado (cifras reales agregadas) | Se agrega la fórmula real de la multa (`días_de_atraso × monto_multa_diaria`, `CEIL` sobre diferencia horaria, sin tope), el valor sembrado (`0.50`) y los códigos `LB404`/`LB409`/`LB422` de `sp_registrar_devolucion.sql`. | 2026-09-07 |
| REQ-F-007, REQ-F-016 | Modificado (cifras reales agregadas) | Se declara que `diasPrestamo` es obligatorio, `@Min(1)`, sin máximo validado; valor sugerido en UI = `dias_prestamo_default` (`15`, `db/seed.sql`). | 2026-09-07 |
| REQ-F-011 | Modificado (corrección de mecanismo, no solo cifra) | Se documentaba/asumía un plazo de retiro vía `minutos_reserva`; se verificó que **ningún código lee esa clave** — el mecanismo real es `hora_limite_retiro_reserva` (default `"18:00"`) aplicado como hora de corte del día de retiro, no como TTL en minutos. Se corrige y se deja `PENDIENTE_VERIFICAR_MARLON` sobre el propósito de `minutos_reserva`. | 2026-09-07 |
| REQ-F-018, REQ-F-017 | Modificado (cifras reales + gap de validación) | Se agrega `max_renovaciones_default = 2` (`db/seed.sql`) y se documenta que `ConfiguracionSistemaService.actualizar()` no valida el nuevo valor contra ningún rango/formato al escribir. | 2026-09-07 |
| REQ-F-026 | Modificado (cifras reales agregadas) | Conjunto cerrado `{dia, semana, mes}`, comparación insensible a mayúsculas, `null` → default `"dia"` (no rechazo). | 2026-09-07 |
| REQ-F-028 | Modificado (cifras reales agregadas) | Límite del chatbot: `10` mensajes por usuario cada `60` segundos (`app.gemini.rate-limit-*`, valores default de `application.yml`). | 2026-09-07 |
| REQ-NF-002 | Modificado (corrección de cifra) | El rationale citaba "7 días" para la vida del `refreshToken`; `application.yml` (`jwt.refresh-expiration-ms: 10800000`) confirma que son **3 horas**, no 7 días. Se corrige y se suben ambos tiempos de vida (`accessToken` 1h, `refreshToken` 3h) al criterio de aceptación. | 2026-09-07 |
| — (sección 1.3.1 nueva) | Agregado | Tabla de estados cerrados y transiciones de las 5 entidades del dominio, con 3 notas de honestidad sobre estados sembrados sin transición real en el código (`VENCIDO`, `EN_REPARACION`/`PERDIDO`) y una referencia a `PENDIENTE` de `estados_libro` que el código busca pero el seed no siembra. | 2026-09-07 |

## Fecha de las entradas

Todas las entradas de esta tabla corresponden a un único commit real,
`4b5d09b` (2026-08-12), que introdujo la actualización del SRS a v1.0.0.
Esta bitácora es una **consolidación retroactiva**: se escribió después de
ese commit, comparando los dos archivos `.md` ya versionados, no en el
momento exacto de cada cambio individual dentro de esa actualización — no
existe un commit separado por requisito porque los 13 requisitos nuevos y
los 2 modificados se agregaron juntos en una sola tarea de documentación.
