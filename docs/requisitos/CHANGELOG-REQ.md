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

## División de requisitos compuestos (Bloque 4, 2026-09-07)

| ID anterior | IDs nuevos | Motivo |
|---|---|---|
| `REQ-NF-014` | `REQ-NF-014a` (CSP backend+frontend), `REQ-NF-014b` (supresión de stacktraces), `REQ-NF-014c` (Swagger desactivado en prod), `REQ-NF-014d` (contenedor sin root) | El ID único agrupaba 4 controles OWASP A05 independientes, cada uno con su propio criterio de aceptación y estado verificable por separado (los 4 están implementados, pero eso no siempre fue ni será necesariamente cierto a la vez para los 4). |
| `REQ-F-022` | `REQ-F-022a` (alerta préstamo por vencer, job cada 60s), `REQ-F-022b` (alerta multa generada, disparada por evento en `sp_registrar_devolucion`), `REQ-F-022c` (alerta reserva caducada, job cada 15 min) | El ID único agrupaba 3 alertas con periodicidades y disparadores distintos (dos jobs con frecuencias distintas + un disparador por evento). Los 3 comparten el mismo estado real: notificación in-app persistida siempre, envío por correo deshabilitado por defecto desde `OBS-23` (saturación SMTP por el volumen sintético de la rúbrica ADB) — declarado explícitamente en cada sub-requisito, no como si el correo funcionara. |

Matriz actualizada: cada fila única se reemplazó por N filas (una por
sub-ID). Todas las referencias sueltas a `REQ-NF-014`/`REQ-F-022` en
`SRS-v1.0.0.md` (sección 5, sección 6, REQ-F-021) se actualizaron al
sub-ID correspondiente. `scripts/validate-traceability.sh` corrido tras
cada división: mismos 3 errores esperados de `REQ-F-029/030/031`
(identificados en el Bloque 3, pendientes de redactar en el Bloque 5), 0
errores nuevos introducidos por las divisiones.

## Requisitos nuevos, funcionalidad implementada sin especificar (Bloque 5, 2026-09-07)

25 requisitos nuevos (`REQ-F-032`-`042`, `REQ-NF-016`-`024`) más la
redacción de 3 ya existentes en la matriz sin entrada en el SRS
(`REQ-F-029/030/031`, identificados en el Bloque 3). Todos corresponden a
funcionalidad **ya implementada** en el código, verificada contra el
código fuente en este commit antes de redactar cada uno — ninguno agrega
alcance funcional nuevo al sistema.

| ID | Título | Hallazgo relevante |
|---|---|---|
| REQ-F-029 | Carga y consulta de portada de libro | — |
| REQ-F-030 | Dashboard gerencial | — |
| REQ-F-031 | Catálogos maestros | Ampliado a `AutorController`/`CategoriaController`; **hueco real**: `POST` sin `@PreAuthorize`, cualquier autenticado (incl. LECTOR) puede escribir |
| REQ-F-032 | Solicitud de restablecimiento de contraseña | Responde distinto (404 vs 204) para correo existente/inexistente — permite enumeración |
| REQ-F-033 | Restablecimiento efectivo de contraseña | No invalida sesiones/tokens activos al resetear |
| REQ-F-034 | Reenvío de código de verificación | Sin límite de reenvíos ni ventana — hallazgo de seguridad pendiente |
| REQ-F-035 | Favoritos por usuario | — |
| REQ-F-036 | Sugerencias de adquisición | — |
| REQ-F-037 | Portal público de consulta sin cuenta | Confirmado: no expone datos personales ni stock por usuario |
| REQ-F-038 | Registro de daños de ejemplares | `EN_REPARACION`/`PERDIDO` sin transición automática (ya en 1.3.1) |
| REQ-F-039 | Gestión de proveedores | — |
| REQ-F-040 | Suscripción a disponibilidad | — |
| REQ-F-041 | Pago parcial de multa | Complementa REQ-F-014 |
| REQ-F-042 | Respaldo y restauración | Restauración desde `backup-service` NO existe todavía (brecha conocida en `BACKUP.md`) |
| REQ-NF-016 | Redis fail-open/closed por servicio | `JwtAuthFilter`/`VerificacionCorreoService` fail-closed; `LoginRateLimiter`/`ChatbotRateLimiter` fail-open |
| REQ-NF-017 | Umbral de rendimiento (p95) | p95 caliente 19.49ms, p95 frío 7.50ms, 50 VUs |
| REQ-NF-018 | Usabilidad SUS | N=0 (muestra anterior retractada, OBS-08) — no se usa la cifra 82.17 |
| REQ-NF-019 | Accesibilidad (Lighthouse) | 95/100, cumple; reporte no cita nivel WCAG específico |
| REQ-NF-020 | SEO del portal público (Lighthouse) | 82/100, **no cumple** el umbral ≥90; causas: meta-description y robots.txt |
| REQ-NF-021 | Objetivos de backup/recovery | PITR Neon 6h/1GB; retención proyecto hasta 2026-09-16; RPO/RTO formales no declarados |
| REQ-NF-022 | Protección de datos personales | Minimización verificada; retención de bitácora y supresión a solicitud del titular **sin política definida** |
| REQ-NF-023 | Política de contraseñas | Solo longitud mínima (8, sin composición); sin endpoint de cambio autenticado |
| REQ-NF-024 | Interfaces externas consumidas (SMTP/Gemini) | Ambas con manejo de error real y fallback |

**Hallazgo transversal más significativo de este bloque**: al construir
A23 (matriz de permisos), se encontró que `PrestamoController.crear`/
`.registrarDevolucion` (ruta simple) excluyen `BIBLIOTECARIO` de
`@PreAuthorize`, contradiciendo la descripción de REQ-F-007/REQ-F-008/
REQ-F-016 (que documentan a `BIBLIOTECARIO` como actor principal). Para
devolución existe una ruta alterna real accesible (`DevolucionController`);
para creación de préstamo no se encontró ninguna. Se corrigieron
REQ-F-007/008/016 con esta nota; el hallazgo no se resuelve en código en
esta tarea (es una tarea de documentación de requisitos, no de
desarrollo) — queda declarado para que el equipo decida.

`scripts/validate-traceability.sh` corrido tras completar el bloque:
**71 filas, 0 problemas** (confirma que los 3 pendientes del Bloque 3 ya
quedaron resueltos).

## Forma, lenguaje y presentación (Bloque 6, 2026-09-07)

| Punto | Cambio |
|---|---|
| M13 | Convención agregada a sección 3.0: "debe" es vinculante, "debería" no se usa en el enunciado de ningún requisito. Reescritos: `REQ-NF-012` ("deberían viajar" → "deben viajar"); `REQ-NF-008`/`REQ-NF-009` (presente indicativo "el sistema usa/se orquestan" → "el sistema debe usar/deben orquestarse"). `REQ-NF-014a`-`d` ya habían quedado en forma vinculante al dividirse en el Bloque 4. |
| M15 | `REQ-F-018` y `REQ-NF-010`: criterios de aceptación reescritos en términos de respuesta HTTP observable (`409 Conflict`/`422 Unprocessable Entity` + `ProblemDetail`), verificado contra `GlobalExceptionHandler.java`. Nombres de excepción Java y `SQLSTATE` movidos al campo `modulo_codigo` de la matriz. |
| M16 | Ya cubierto por M24 (Bloque 3) — confirmado aplicado: sin hashes muertos restantes en `SRS-v1.0.0.md` (`grep` de los 6 hashes citados, 0 resultados). |
| M25 | Sección 1.6 nueva: tabla de correspondencia entre la estructura IEEE 830 de este SRS y la plantilla informativa de Anexo C de 29148:2018, con nota de honestidad explícita sobre no tener copia local del estándar para verificar numeración exacta de cláusula. No se reordena el documento (opción ya aceptada por el docente). |
| M26 | Sección 3.3 dividida en 3.3.1 (interfaz expuesta: API REST propia) y 3.3.2 (interfaces consumidas: SMTP/Gemini, referencia a `REQ-NF-024`). Se corrige la afirmación anterior de "única interfaz externa real", que mezclaba expuesta y consumida. |
| M27 | Nueva sección "Historial de revisiones" tras la portada: fecha de emisión (2026-09-07) y tabla de 3 versiones (v0.9.0-rc, v1.0.0 previo, v1.0.0 esta revisión), enlazando a este mismo changelog para el detalle línea por línea. |
| M28 | Campo `- **Estado**: <valor>` agregado mecánicamente (script Python, no edición manual) a los 64 requisitos que no lo tenían explícito, usando el vocabulario exacto de la columna `estado` de `matriz.csv` (`verificado`/`implementado`/`pendiente`, sin inventar vocabulario nuevo). Los 7 que ya tenían una narrativa "Estado real" propia (`REQ-NF-012`, `REQ-NF-014a`-`d`, `REQ-NF-018`, `REQ-NF-020`) no se duplicaron. |
| M29 | Fragmentos de código/ruta/nombre de método largos envueltos en backticks en línea a lo largo de toda la redacción de este bloque y del Bloque 5, para dar a pandoc/LaTeX más oportunidad de wrap limpio; verificación final contra el PDF real en el paso siguiente. |

`scripts/validate-traceability.sh` corrido tras el bloque: **71 filas, 0
problemas** (el campo `- **Estado**:` no es validado por el script, no
afecta su resultado).

## Fecha de las entradas

Todas las entradas de esta tabla corresponden a un único commit real,
`4b5d09b` (2026-08-12), que introdujo la actualización del SRS a v1.0.0.
Esta bitácora es una **consolidación retroactiva**: se escribió después de
ese commit, comparando los dos archivos `.md` ya versionados, no en el
momento exacto de cada cambio individual dentro de esa actualización — no
existe un commit separado por requisito porque los 13 requisitos nuevos y
los 2 modificados se agregaron juntos en una sola tarea de documentación.

## Revisión docente 2026-09-11 (M1) — vocabulario de estado

| Punto | Cambio |
|---|---|
| M1 | Columna `observaciones` agregada a `docs/trazabilidad/matriz.csv` (12 columnas). `REQ-NF-020`/`REQ-NF-022` limpiados a `estado=implementado`, matiz movido a `observaciones`. `scripts/validate-traceability.sh`: nueva validación 5 (vocabulario cerrado de `estado`: `pendiente`/`implementado`/`verificado`). `SRS.md`: `REQ-NF-022` con paréntesis quitado del campo `Estado`; `REQ-NF-020` con campo `- **Estado**: implementado` agregado (excepción puntual a M28, que lo había excluido por tener narrativa propia — ahora conviven ambos sin duplicar información). |

`scripts/validate-traceability.sh` corrido tras el bloque: **71 filas, 0
problemas** (incluida la validación 5 nueva).
