## CU-CFG-01: Listar y actualizar parámetros del sistema

- **Actor principal**: ADMIN.
- **Interesados y sus intereses**:
  - Administrador: quiere ajustar valores operativos (límites, ventanas,
    montos) sin depender de un despliegue nuevo del backend.
  - Equipo de desarrollo: quiere evitar releases solo para cambiar un
    número que ya vive en `configuracion_sistema`.
- **Precondiciones**: el usuario tiene una sesión válida con rol `ADMIN`;
  la clave a actualizar ya existe en la tabla `configuracion_sistema`
  (`ConfiguracionSistemaService.actualizar` no crea claves nuevas).
- **Garantía de éxito (postcondición)**: el nuevo valor queda persistido y
  visible de inmediato para cualquier consumidor de
  `ConfiguracionSistemaService.obtenerValor*` (la cache en memoria del
  servicio se invalida para esa clave al escribir).
- **Disparador**: el ADMIN abre la pantalla de configuración del sistema
  o llama directamente a la API.

### Escenario principal (flujo básico)

1. El ADMIN pide `GET /api/v1/configuracion`.
2. `ConfiguracionSistemaController` responde `200` con el listado completo
   de claves/valores (`ConfiguracionSistemaService.listar`).
3. El ADMIN envía `PUT /api/v1/configuracion/{clave}` con el nuevo valor.
4. `ConfiguracionSistemaService.actualizar` busca la clave, la actualiza,
   invalida la entrada de cache correspondiente y registra el cambio en
   `bitacora_auditoria`.
5. El sistema responde `200` con el valor nuevo.

### Extensiones (flujos alternativos)

- **3a.** La clave no existe todavía: `EntityNotFoundException` (no se
  crean claves nuevas por esta vía, solo se actualizan las ya sembradas
  en `db/seed.sql`).
- **1a/3a'.** El usuario no tiene rol `ADMIN`: `403` antes de ejecutar
  cualquier lógica de negocio.
- **Nota de honestidad verificada en código**: `actualizar()` no valida el
  nuevo valor contra ningún rango o formato esperado por la clave (acepta
  cualquier cadena, incluida una no numérica para una clave que un
  consumidor espera como entero/decimal); un valor inválido solo falla
  más tarde, al leerse (`obtenerValorEntero`/`obtenerValorDecimal`), no al
  escribirse. Ver REQ-F-017/REQ-F-018 en el SRS para el detalle.
