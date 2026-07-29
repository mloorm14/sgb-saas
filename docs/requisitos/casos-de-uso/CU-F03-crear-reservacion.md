## CU-F03: Crear una reservación desde la interfaz

- **Actor principal**: Lector (usando la interfaz web). Variante:
  Bibliotecario/Gerente reservando a nombre de un usuario.
- **Interesados y sus intereses**:
  - Lector: quiere reservar en pocos clics, sin tener que saber ni
    escribir su propio ID de usuario.
  - Bibliotecario: cuando reserva por un usuario (ej. pedido telefónico),
    quiere un formulario simple con usuario y libro.
- **Precondiciones**: el actor tiene sesión iniciada.
- **Garantía de éxito**: la reservación queda creada a nombre del
  usuario correcto (el propio lector, o el usuario indicado por el
  bibliotecario) y visible de inmediato en el listado correspondiente.
- **Disparador**: el actor completa el formulario "Nueva reservación" y
  confirma.

### Escenario principal (flujo básico)

1. El actor abre la sección de reservaciones.
2. Si es lector, la interfaz precompleta el usuario con su propio id
   (no se lo pide); si es bibliotecario/gerente, la interfaz muestra
   también el campo "Usuario ID".
3. El actor completa el ID del libro y confirma.
4. La interfaz llama a `POST /api/v1/reservaciones`.
5. Si el usuario creado coincide con el que está siendo consultado en
   el listado visible, la tabla se refresca para mostrar la nueva
   reservación.

### Extensiones (flujos alternativos)

- **4a.** El backend rechaza la reservación (libro sin ejemplares
  disponibles, usuario bloqueado por multa, etc.): la interfaz muestra
  `errorMsgCrear` con el detalle y conserva los datos ingresados en el
  formulario para que el actor no tenga que reescribirlos.
- **1a.** El lector aún no tiene sesión válida: el `authGuard` lo
  redirige a `/login` antes de mostrar el formulario.