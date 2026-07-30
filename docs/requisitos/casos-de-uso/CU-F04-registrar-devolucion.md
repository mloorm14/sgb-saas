## CU-F04: Registrar la devolución de un préstamo (bibliotecario)

- **Actor principal**: Bibliotecario (usando la interfaz web).
- **Interesados y sus intereses**:
  - Bibliotecario: quiere marcar la devolución en un clic, sin perder
    de vista el resto de la lista del usuario que está atendiendo.
  - Lector: quiere que el ejemplar quede liberado de inmediato para
    otros usuarios (lo resuelve el backend, no la UI).
- **Precondiciones**: el bibliotecario tiene sesión iniciada; buscó los
  préstamos de un usuario y al menos uno está sin devolver (sin fecha
  de devolución real).
- **Garantía de éxito**: la fila del préstamo se actualiza para
  reflejar la devolución sin recargar toda la tabla ni perder la
  búsqueda o página actual.
- **Disparador**: el bibliotecario hace clic en "Registrar devolución"
  en la fila de un préstamo activo.

### Escenario principal (flujo básico)

1. El bibliotecario busca los préstamos de un usuario por su ID en
   "Gestión de préstamos".
2. Ubica en la tabla un préstamo sin fecha de devolución real.
3. Hace clic en "Registrar devolución" en esa fila.
4. La interfaz muestra una confirmación simple (¿está seguro?).
5. El bibliotecario confirma.
6. La interfaz llama a `POST /api/v1/prestamos/{id}/devolucion`.
7. La tabla se refresca: esa fila ya no muestra el botón y sí la fecha
   real de devolución.

### Extensiones (flujos alternativos)

- **6a.** El préstamo ya estaba devuelto (otro bibliotecario lo procesó
  en paralelo): el backend responde con error; la interfaz muestra
  `errorMsg` y refresca la tabla para reflejar el estado real, en vez
  de dejar la fila desactualizada con un botón que ya no aplica.
- **2a.** Ningún préstamo de ese usuario está pendiente de devolución:
  ninguna fila muestra el botón "Registrar devolución", sin necesidad
  de un mensaje adicional (el estado ya es visible por la presencia de
  la fecha de devolución real en cada fila).