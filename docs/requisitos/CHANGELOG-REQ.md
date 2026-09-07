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
| Sección 2.5 | Modificado (supuesto invertido) | Se afirmaba que `JwtAuthFilter` queda "sin forma de verificar revocaciones" si Redis cae (fail-open implícito). `JwtAuthFilter.java` confirma lo contrario: captura `DataAccessException` y responde `401` (fail-closed). Se corrige y se referencia A15 para el detalle por servicio (`LoginRateLimiter`/`ChatbotRateLimiter` sí son fail-open). | 2026-09-07 |
| REQ-NF-012 | Modificado (estado real de producción) | Se verificó `render.yaml`: `sgb-backend`/`biblora-sgb` corren sin dominio propio bajo `*.onrender.com`, donde Render termina TLS en su borde automáticamente. Se corrige el estado de "pendiente sin distinguir entornos" a "implementado en producción real; solo el stack Docker Compose local sigue en HTTP plano". | 2026-09-07 |
| REQ-NF-014 | Modificado (gap cerrado) | Se verificó `frontend-angular/nginx.conf:10`: ya envía `Content-Security-Policy` con `always`. El gap de CSP del lado frontend que declaraban versiones anteriores de este SRS ya no existe. | 2026-09-07 |
| REQ-NF-004, REQ-NF-005, REQ-NF-008 | Modificado (cifras recontadas 2026-09-07) | Objetos SQL: 7→18 (contados sobre `db/procs/`+`database/migrations/`). Tablas: 26→44 (contadas por `CREATE TABLE` distintos en `database/migrations/`). REQ-NF-005 agrega nota de honestidad: el criterio de reproducibilidad desde volumen vacío **no se cumple hoy** (ver `OBS-25`). | 2026-09-07 |
| Sección 6, punto 9 | Modificado (cifra recontada) | ADRs: 13→14 (`adr-029-v29-gap.md` agregado después del último conteo). | 2026-09-07 |
| Sección 3.3 | Modificado (cifras recontadas) | Controladores: 15→31 (30 con lógica de negocio + `TestController`). Rutas: 44→143 combinaciones método+ruta. | 2026-09-07 |
| — | Identificado, no redactado todavía | `REQ-F-029`, `REQ-F-030`, `REQ-F-031` existen en la matriz (46 filas) sin entrada en la sección 3 del SRS. Se redactan como A7/A8/A9 en el Bloque 5. | 2026-09-07 |
| REQ-F-023 | Modificado (contradicción de código encontrada al redactar HU-ADM-01) | Se documentaba a `GERENTE` como de solo lectura sobre el padrón de usuarios, con `403` en bloque al intentar cambiar rol/estado. `UsuarioAdminController.java` muestra `@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")` en las rutas de escritura; la restricción real (rol limitado a LECTOR/BIBLIOTECARIO, estado limitado a ACTIVO/INACTIVO, solo sobre usuarios creados por el propio GERENTE) vive en `UsuarioAdminService`, no en el endpoint. Se corrige. | 2026-09-07 |
| REQ-F-024 | Modificado (endpoints reales agregados) | `AuditoriaController` expone también `GET /api/v1/auditoria/resumen` y `GET /api/v1/auditoria/export`, no documentados en versiones anteriores. Se agregan al requisito y a la matriz. | 2026-09-07 |
| — | Agregado (9 archivos) | Se crearon las historias/casos de uso faltantes citados por el SRS sin archivo real: `HU-CFG-01`, `CU-CFG-01` (REQ-F-017); `HU-PRE-03`, `CU-07` (REQ-F-018); `HU-PRE-04` (REQ-F-019, reutiliza `CU-01` existente); `HU-ADM-01`, `CU-ADM-01` (REQ-F-023); `HU-AUD-01`, `CU-AUD-01` (REQ-F-024). | 2026-09-07 |
| — | Modificado (`scripts/validate-traceability.sh`) | Se agregaron 2 validaciones nuevas: (3) todo `id_requisito` de la matriz debe tener encabezado en el SRS y viceversa; (4) todo valor de `historia_usuario`/`caso_de_uso` con forma de ID debe corresponder a un archivo real (con caso especial para los IDs legados de Cajas, consolidados en `historias-usuario.md`/`casos-de-uso.md`). Corrida sobre el estado de este bloque: **3 errores esperados** (`REQ-F-029/030/031` sin encabezado en el SRS, gap ya identificado arriba y diferido al Bloque 5 a propósito, no una regresión de esta validación). | 2026-09-07 |
| Múltiples (REQ-F-004 criterio 3; portada del documento; sección 4) | Modificado (M24, hashes muertos) | Se reemplazaron todas las referencias a hashes de commit invalidados por la reescritura de `git-filter-repo` (`adca044`, `51607f3`, `8ce7b9e`, `6c351cf`) por redacciones que anclan al tag `v1.0.0` (commit `16279881`) o que declaran explícitamente la invalidación sin inventar un hash nuevo. El hash de REQ-F-004 criterio 3 se trasladó de criterio de aceptación a `evidencia_empirica` en la matriz. | 2026-09-07 |

## Fecha de las entradas

Todas las entradas de esta tabla corresponden a un único commit real,
`4b5d09b` (2026-08-12), que introdujo la actualización del SRS a v1.0.0.
Esta bitácora es una **consolidación retroactiva**: se escribió después de
ese commit, comparando los dos archivos `.md` ya versionados, no en el
momento exacto de cada cambio individual dentro de esa actualización — no
existe un commit separado por requisito porque los 13 requisitos nuevos y
los 2 modificados se agregaron juntos en una sola tarea de documentación.
