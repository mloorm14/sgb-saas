## CU-AUD-01: Consultar, resumir y exportar la bitácora de auditoría

- **Actor principal**: GERENTE o ADMIN.
- **Interesados y sus intereses**:
  - Gerente/Administrador: quiere poder investigar un incidente o
    verificar quién hizo qué, sin necesitar acceso directo a la base de
    datos.
  - Equipo de seguridad: quiere que la bitácora sea consultable sin abrir
    una vía de acceso para roles que no la necesitan.
- **Precondiciones**: el usuario tiene sesión válida con rol `GERENTE` o
  `ADMIN` (restricción a nivel de clase en `AuditoriaController`,
  `@PreAuthorize("hasAnyRole('GERENTE','ADMIN')")`, aplicada a las tres
  rutas del controller).
- **Garantía de éxito (postcondición)**: el resultado devuelto refleja
  exactamente los filtros aplicados (`usuarioId`, `modulo`, `desde`,
  `hasta`), sin exponer eventos fuera de esos criterios.
- **Disparador**: `GET /api/v1/auditoria`, `GET /api/v1/auditoria/resumen`,
  o `GET /api/v1/auditoria/export`.

### Escenario principal (flujo básico)

1. El usuario pide el listado paginado de eventos, opcionalmente con
   filtros de `usuarioId`/`modulo`/`desde`/`hasta`.
2. `AuditoriaService.listar` consulta `bitacora_auditoria` con esos
   filtros, ordenado por `fecha_hora` descendente por defecto.
3. El sistema responde `200` con la página de resultados.
4. Alternativamente, el usuario pide `/resumen`: `AuditoriaService.resumen`
   agrega el total de eventos por `tabla_afectada`, con el total de hoy y
   el último evento por categoría.
5. Alternativamente, el usuario pide `/export?formato=csv` (con los mismos
   filtros que el listado): `AuditoriaService.exportarCsv` genera el CSV
   en memoria y el controller responde con
   `Content-Disposition: attachment; filename=auditoria.csv`.

### Extensiones (flujos alternativos)

- **1a/4a/5a.** El usuario no tiene rol `GERENTE`/`ADMIN`: `403` desde el
  `@PreAuthorize` de clase, antes de ejecutar cualquier consulta.
- **Nota de honestidad**: las rutas `/resumen` y `/export` son
  funcionalidad real de `AuditoriaController.java` que no estaba
  documentada en versiones anteriores del SRS (solo se documentaba
  `GET /api/v1/auditoria`) — se agregan aquí y en REQ-F-024 tras verificar
  el controller completo en este commit.
