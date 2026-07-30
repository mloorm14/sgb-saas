# Casos de uso — Módulo de Préstamos/Devoluciones/Reservaciones/Multas

Formato Cockburn, siguiendo la plantilla de
`docs/reparto-entrega-3/cajas-backend/INSTRUCCIONES.md` (sección 9).
Cada caso de uso corresponde 1:1 con su historia de usuario en
`historias-usuario.md`.

## CU-01: Registrar préstamo

- **Actor principal**: Bibliotecario
- **Interesados y sus intereses**:
  - Bibliotecario: quiere registrar el préstamo rápido, sin pasos manuales.
  - Lector: quiere llevarse el libro solo si no tiene impedimentos (multas, stock).
  - Gerente: quiere que el stock del catálogo siempre sea confiable.
- **Precondiciones**: el bibliotecario tiene sesión iniciada con rol
  BIBLIOTECARIO o GERENTE; el libro y el usuario existen en el sistema.
- **Garantía de éxito (postcondición)**: existe un registro nuevo en
  `prestamos` con estado ACTIVO; `libros.stock_disponible` del libro
  prestado se decrementó en 1; ambas escrituras ocurrieron en la misma
  transacción atómica (`sp_crear_prestamo`).
- **Disparador**: el bibliotecario selecciona "Nuevo préstamo" en la
  interfaz e ingresa el usuario y el libro.

### Escenario principal (flujo básico)

1. El bibliotecario ingresa el correo del lector y el ISBN/título del libro.
2. El sistema valida que el usuario existe y su estado es ACTIVO.
3. El sistema valida que el libro existe y tiene stock disponible > 0.
4. El sistema registra el préstamo (`sp_crear_prestamo`), decrementando
   el stock del libro en la misma transacción.
5. El sistema confirma al bibliotecario el préstamo creado con su fecha
   de devolución estimada.

### Extensiones (flujos alternativos)

- **3a.** El libro no tiene stock disponible: el sistema rechaza la
  operación con error 422 ("sin stock disponible") y no crea ningún
  registro.
- **2a.** El usuario tiene estado BLOQUEADO_POR_MULTA: el sistema
  rechaza la operación con error 422 ("usuario con multas pendientes")
  y no crea ningún registro.
- **1a.** El usuario o el libro no existen: el sistema rechaza con error
  404.

## CU-02: Registrar devolución

- **Actor principal**: Bibliotecario
- **Interesados y sus intereses**:
  - Bibliotecario: quiere liberar el ejemplar y saber de inmediato si
    corresponde una multa.
  - Lector: quiere que la devolución se refleje de inmediato en su
    historial y, si se atrasó, entender por qué queda bloqueado.
  - Gerente: quiere que ninguna devolución se registre dos veces sobre
    el mismo préstamo.
- **Precondiciones**: el préstamo existe y está en estado ACTIVO; el
  bibliotecario tiene sesión iniciada con rol BIBLIOTECARIO o GERENTE.
- **Garantía de éxito (postcondición)**: el préstamo cambia a estado
  DEVUELTO; el stock del libro se incrementa en 1; si hubo atraso, se
  crea una multa en estado PENDIENTE y el usuario pasa a
  BLOQUEADO_POR_MULTA — todo en la misma transacción atómica
  (`sp_registrar_devolucion`).
- **Disparador**: el bibliotecario selecciona "Registrar devolución"
  sobre un préstamo activo.

### Escenario principal (flujo básico)

1. El bibliotecario indica el id del préstamo a devolver.
2. El sistema valida que el préstamo existe y sigue ACTIVO.
3. El sistema calcula si la fecha actual supera la fecha límite pactada.
4. El sistema registra la devolución (`sp_registrar_devolucion`),
   incrementando el stock del libro.
5. Si hubo atraso, el sistema genera una multa PENDIENTE y bloquea al
   usuario en la misma transacción.
6. El sistema confirma al bibliotecario si hubo o no multa y su monto.

### Extensiones (flujos alternativos)

- **2a.** El préstamo ya está en estado DEVUELTO: el sistema rechaza la
  operación con error 409 ("el préstamo ya fue devuelto").
- **1a.** El préstamo no existe: el sistema rechaza con error 404.

## CU-03: Crear reservación

- **Actor principal**: Lector (también puede ejecutarlo un Bibliotecario
  o Gerente en nombre de un lector).
- **Interesados y sus intereses**:
  - Lector: quiere asegurarse un ejemplar sin stock disponible en el
    momento.
  - Bibliotecario: quiere poder registrar reservaciones a pedido de un
    lector que no tiene acceso al sistema.
  - Gerente: quiere que nadie reserve en nombre de otro usuario sin
    autorización.
- **Precondiciones**: el usuario tiene sesión iniciada (cualquier rol);
  el libro existe en el catálogo.
- **Garantía de éxito (postcondición)**: existe un registro nuevo en
  `reservaciones` con `estado_reservacion_id` = PENDIENTE,
  `fecha_reserva` = momento de la operación, y una `fecha_limite_retiro`
  calculada.
- **Disparador**: el usuario selecciona "Reservar" sobre un libro sin
  stock disponible.

### Escenario principal (flujo básico)

1. El usuario indica el libro que desea reservar.
2. El sistema resuelve el usuario destino: si quien reserva es LECTOR,
   siempre es él mismo (se ignora cualquier `usuarioId` distinto que
   venga en el request); si es BIBLIOTECARIO/GERENTE, puede indicarse
   otro usuario en el DTO.
3. El sistema resuelve el estado inicial PENDIENTE desde
   `EstadoReservacionRepository`.
4. El sistema calcula la fecha límite de retiro según la regla de
   negocio acordada.
5. El sistema crea la reservación y confirma al usuario los datos.

### Extensiones (flujos alternativos)

- **1a.** El libro no existe: el sistema rechaza con error 404.
- **2a.** El usuario destino no existe (caso bibliotecario reservando
  para otro): el sistema rechaza con error 404.

## CU-04: Pagar multa

- **Actor principal**: Bibliotecario
- **Interesados y sus intereses**:
  - Bibliotecario: quiere desbloquear al lector en cuanto se confirme
    el pago.
  - Lector: quiere recuperar la posibilidad de pedir préstamos apenas
    paga.
  - Gerente: quiere que el desbloqueo solo ocurra si no quedan otras
    multas pendientes.
- **Precondiciones**: la multa existe y está en estado PENDIENTE; el
  bibliotecario tiene sesión iniciada con rol BIBLIOTECARIO o GERENTE.
- **Garantía de éxito (postcondición)**: la multa cambia a estado
  PAGADA; si era la última multa PENDIENTE del usuario, el usuario
  vuelve a estado ACTIVO — todo en la misma transacción atómica
  (`sp_pagar_multa`).
- **Disparador**: el bibliotecario selecciona "Registrar pago" sobre
  una multa pendiente.

### Escenario principal (flujo básico)

1. El bibliotecario indica el id de la multa pagada.
2. El sistema valida que la multa existe y está PENDIENTE.
3. El sistema registra el pago (`sp_pagar_multa`).
4. El sistema verifica si el usuario tiene otras multas PENDIENTE.
5. Si no tiene ninguna otra, el sistema desbloquea al usuario
   (ACTIVO) en la misma transacción.
6. El sistema confirma al bibliotecario si el usuario quedó desbloqueado.

### Extensiones (flujos alternativos)

- **2a.** La multa ya está PAGADA o ANULADA: el sistema rechaza la
  operación con error 409.
- **1a.** La multa no existe: el sistema rechaza con error 404.
- **5a.** El usuario tiene otra multa PENDIENTE: el sistema registra el
  pago igual, pero el usuario permanece BLOQUEADO_POR_MULTA.

## CU-05: Anular multa

- **Actor principal**: Gerente (o Administrador)
- **Interesados y sus intereses**:
  - Gerente: quiere poder corregir una multa mal aplicada, dejando
    rastro de auditoría de quién y por qué.
  - Bibliotecario: no debe poder ejecutar esta acción bajo ninguna
    circunstancia, ni siquiera manipulando el request.
  - Lector: quiere que, si la multa era un error, se corrija sin tener
    que pagarla.
- **Precondiciones**: la multa existe y está en estado PENDIENTE; quien
  ejecuta la acción tiene sesión iniciada con rol GERENTE o ADMIN
  (verificado únicamente desde la sesión autenticada, nunca desde el
  cuerpo del request).
- **Garantía de éxito (postcondición)**: la multa cambia a estado
  ANULADA; se inserta una fila en `bitacora_auditoria` documentando la
  anulación (con `usuario_id` en NULL, limitación conocida del
  procedimiento, ya que este no recibe el id de quien ejecuta) — todo
  en la misma transacción atómica (`sp_anular_multa`).
- **Disparador**: el gerente selecciona "Anular" sobre una multa
  pendiente e ingresa un motivo.

### Escenario principal (flujo básico)

1. El gerente indica el id de la multa e ingresa el motivo de anulación.
2. El sistema resuelve el rol ejecutor desde `Authentication`
   (`auth.getAuthorities()`), nunca desde el body del request.
3. El `@PreAuthorize` del controller ya restringe el endpoint a
   GERENTE/ADMIN antes de llegar al service.
4. El sistema invoca `sp_anular_multa` pasando el rol real resuelto.
5. El procedimiento valida nuevamente el rol como defensa en
   profundidad (rechaza con LB422 si no es GERENTE/ADMIN), cambia la
   multa a ANULADA, y registra la fila en `bitacora_auditoria`.
6. El sistema confirma al gerente la anulación.

### Extensiones (flujos alternativos)

- **3a.** Un usuario con rol BIBLIOTECARIO intenta el endpoint: el
  `@PreAuthorize` del controller rechaza con error 403 antes de
  ejecutar cualquier lógica de negocio.
- **1a.** Un BIBLIOTECARIO envía `"rolEjecutor": "GERENTE"` en el body
  del request: el campo se ignora por completo — el DTO de request no
  expone ese campo, y el rol se resuelve siempre de la sesión real. El
  intento se rechaza igual (403, por el mismo motivo del punto 3a).
- **1b.** La multa no existe o ya no está PENDIENTE: el sistema rechaza
  con error 404 o 409 respectivamente.
