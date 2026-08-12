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

## Fecha de las entradas

Todas las entradas de esta tabla corresponden a un único commit real,
`4b5d09b` (2026-08-12), que introdujo la actualización del SRS a v1.0.0.
Esta bitácora es una **consolidación retroactiva**: se escribió después de
ese commit, comparando los dos archivos `.md` ya versionados, no en el
momento exacto de cada cambio individual dentro de esa actualización — no
existe un commit separado por requisito porque los 13 requisitos nuevos y
los 2 modificados se agregaron juntos en una sola tarea de documentación.
