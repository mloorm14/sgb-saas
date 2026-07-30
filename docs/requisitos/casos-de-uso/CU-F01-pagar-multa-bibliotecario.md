## CU-F01: Pagar una multa desde la vista de gestión (bibliotecario)

- **Actor principal**: Bibliotecario (usando la interfaz web).
- **Interesados y sus intereses**:
  - Bibliotecario: quiere completar el cobro en pocos clics, sin
    recargar la página ni perder el filtro/paginación en el que estaba.
  - Lector: quiere que su bloqueo se levante inmediatamente después del
    pago, sin demoras.
- **Precondiciones**: el bibliotecario tiene sesión iniciada; existe al
  menos una multa en estado PENDIENTE visible en la lista.
- **Garantía de éxito**: la fila de la multa en la tabla se actualiza a
  "Pagada" sin recargar toda la página; si el pago desbloqueó al
  usuario, no hace falta ninguna acción adicional en la UI (el backend
  ya lo resuelve).
- **Disparador**: el bibliotecario hace clic en "Pagar" en la fila de
  una multa pendiente.

### Escenario principal (flujo básico)

1. El bibliotecario ubica la multa pendiente en la tabla paginada de
   "Gestión de multas".
2. Hace clic en el botón "Pagar" de esa fila.
3. La interfaz muestra una confirmación simple (¿está seguro?).
4. El bibliotecario confirma.
5. La interfaz llama a `POST /api/v1/multas/{id}/pago`.
6. La fila se actualiza a estado "Pagada" sin recargar el resto de la
   tabla ni perder la página actual de paginación.

### Extensiones (flujos alternativos)

- **5a.** La multa ya estaba pagada/anulada (otro bibliotecario la
  procesó en paralelo): el backend responde 409; la UI muestra
  `errorMsg` ("Esta multa ya fue procesada") y refresca la fila para
  reflejar el estado real, en vez de dejar la fila desactualizada.
- **1a.** No hay multas pendientes: la tabla muestra un mensaje vacío
  ("No hay multas pendientes") en vez de una tabla en blanco sin
  explicación.