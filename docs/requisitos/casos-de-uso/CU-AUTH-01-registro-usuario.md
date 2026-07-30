## CU-AUTH-01: Registrar un usuario nuevo

- **Actor principal**: Visitante (sin cuenta previa en el sistema).
- **Interesados y sus intereses**:
  - Visitante: quiere una cuenta lista para usar de inmediato, sin pasos
    de verificación manual que lo bloqueen.
  - Gerente/Administrador: quiere que todo usuario nuevo entre con el
    rol mínimo (LECTOR) — nadie se auto-asigna un rol superior.
- **Precondiciones**: ninguna — cualquier visitante puede llegar a este
  endpoint sin sesión iniciada.
- **Garantía de éxito (postcondición)**: existe una fila nueva en
  `usuarios` con `estado_id` = ACTIVO, `correo_verificado` = false, un
  único rol asignado (`LECTOR`), y `password_hash` con la contraseña
  hasheada (BCrypt vía `PasswordEncoder`), nunca en texto plano.
- **Disparador**: el visitante completa y envía el formulario de
  registro (`POST /api/auth/registro`).

### Escenario principal (flujo básico)

1. El visitante ingresa nombre, apellido, correo y contraseña.
2. El sistema valida el formato del correo y que la contraseña tenga al
   menos 8 caracteres (Bean Validation, `RegistroRequestDTO`).
3. El sistema verifica que no exista ya un usuario con ese correo.
4. El sistema resuelve el rol `LECTOR` y el estado `ACTIVO` desde los
   catálogos (`roles`, `estados_usuario`).
5. El sistema crea el usuario con la contraseña hasheada y confirma con
   `201 Created` y los datos del usuario (sin exponer `password_hash`).

### Extensiones (flujos alternativos)

- **2a.** Datos inválidos (correo mal formado, contraseña corta, campos
  vacíos): el sistema rechaza con error 400 y el detalle de qué campo
  falló (`GlobalExceptionHandler.handleValidation`).
- **3a.** El correo ya está registrado: el sistema rechaza con error 409
  (`CorreoYaRegistradoException`) y no crea ningún registro.
