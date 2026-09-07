## CU-07: Renovar un préstamo activo

- **Actor principal**: LECTOR (su propio préstamo) o
  BIBLIOTECARIO/GERENTE/ADMIN (cualquier préstamo).
- **Interesados y sus intereses**:
  - Lector: quiere quedarse con el libro más tiempo sin devolverlo
    físicamente si todavía lo necesita.
  - Biblioteca: quiere que el libro rote razonablemente y no quede
    indefinidamente en manos de un solo lector, ni pise una reserva ya
    hecha por otro usuario.
- **Precondiciones**: existe un préstamo con `estado_prestamo_id` distinto
  de `DEVUELTO`; el usuario ejecutor tiene sesión válida.
- **Garantía de éxito (postcondición)**: la fecha límite de devolución se
  extiende `dias_prestamo_default` días desde el momento de la renovación,
  el contador `renovacionesRealizadas` aumenta en 1, y el préstamo queda
  en estado `RENOVADO`.
- **Disparador**: `POST /api/v1/prestamos/{id}/renovacion`.

### Escenario principal (flujo básico)

1. El usuario pide renovar un préstamo por su id.
2. `PrestamoService.renovar` verifica, en este orden: (a) que el préstamo
   exista; (b) que el usuario tenga acceso (dueño del préstamo o rol
   BIBLIOTECARIO/GERENTE/ADMIN); (c) que no esté `DEVUELTO`; (d) que la
   fecha límite actual no esté vencida; (e) que
   `renovacionesRealizadas < max_renovaciones_default`; (f) que no exista
   una reserva vigente (`PENDIENTE`/`LISTA_PARA_RETIRO`) de otro usuario
   sobre el mismo libro.
3. Si todas las condiciones se cumplen, se extiende la fecha límite en
   `dias_prestamo_default` días, se incrementa el contador de
   renovaciones y el préstamo pasa a `RENOVADO`.
4. El sistema responde con los datos de la renovación.

### Extensiones (flujos alternativos)

- **2a.** El préstamo no existe: `404`.
- **2b.** Un LECTOR intenta renovar un préstamo ajeno: acceso denegado.
- **2c.** El préstamo ya está `DEVUELTO`: rechazo explícito
  (`IllegalArgumentException`).
- **2d.** El préstamo está vencido (fecha límite ya pasada):
  `PrestamoVencidoException`.
- **2e.** Ya alcanzó el máximo de renovaciones:
  `LimiteRenovacionesExcedidoException`.
- **2f.** Existe una reserva vigente de otro usuario sobre el libro:
  `MaterialReservadoException`.
