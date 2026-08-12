# Versionado — SGB-SaaS

Este proyecto adopta [Semantic Versioning 2.0.0](https://semver.org/lang/es/)
(SemVer) para todos los tags de release, requisito del Bloque E de la
Tercera Entrega.

## Regla general

Dado un número de versión `MAJOR.MINOR.PATCH`:

- **MAJOR** se incrementa cuando hay un cambio incompatible con versiones
  anteriores (breaking change).
- **MINOR** se incrementa cuando se agrega funcionalidad nueva de forma
  compatible con versiones anteriores.
- **PATCH** se incrementa cuando se corrige un bug de forma compatible con
  versiones anteriores.

## Qué cuenta como cada tipo de cambio en SGB-SaaS

No es un ejercicio abstracto: los siguientes son ejemplos concretos de
cambios reales o plausibles en este proyecto específico, para que
cualquier integrante pueda decidir el próximo número de versión sin
ambigüedad.

### MAJOR (incompatible)

- **Un cambio de schema de base de datos que rompe compatibilidad**: por
  ejemplo, eliminar o renombrar una columna que el frontend o un
  integrador externo ya consume (`libros.stock_disponible` cambiando de
  nombre), o una migración de Flyway que no es aditiva y exige coordinar
  el despliegue del backend y el frontend en el mismo instante.
- **Un cambio de contrato de la API REST que un cliente existente no
  puede ignorar**: por ejemplo, si se migrara el `accessToken` a cookie
  HttpOnly (cambio ya identificado como pendiente en
  `docs/adr/adr-007-cookies-jwt.md`) — cualquier cliente que hoy dependa
  de leer el `accessToken` del cuerpo JSON dejaría de funcionar sin
  cambios de su parte.
- **Retirar un endpoint o un rol** que un cliente externo ya usa.

### MINOR (funcionalidad nueva compatible)

- **Un módulo funcional nuevo**: el ejemplo canónico de la Tercera
  Entrega es el módulo de **Préstamos/Reservas/Multas** — agrega
  endpoints y tablas nuevas, pero no modifica ni rompe nada de lo que ya
  existe (catálogo, autenticación). Para la Entrega Final, los módulos
  de **notificaciones y verificación de correo**, **credencial QR**,
  **favoritos y sugerencias de adquisición**, y **panel de
  administración con reportes** (todos de Cajas) siguen el mismo
  criterio: agregan superficie nueva de forma aditiva, sin romper
  contratos existentes.
- **Un endpoint nuevo sobre un recurso existente** que no cambia el
  contrato de los endpoints ya existentes (ej. un endpoint de reportes
  agregados sobre libros más prestados,
  `fn_reporte_libros_mas_prestados`).
- **Un campo nuevo opcional** agregado a una respuesta JSON existente,
  que un cliente que lo ignore sigue funcionando exactamente igual.

### PATCH (corrección de bug compatible)

- **Un fix de bug que no cambia el contrato de la API**: el ejemplo real
  de este proyecto es el commit `0f1b980`
  (`fix(backend): GlobalExceptionHandler responde RFC 7807 en todos los
  handlers`), que agregó el handler faltante para `LockedException`
  (cuenta bloqueada por multas, antes caía al catch-all genérico y
  devolvía un 500 sin distinción) — corrige un comportamiento incorrecto
  sin cambiar ningún endpoint ni romper a ningún cliente que ya
  funcionara correctamente.
- **Correcciones de seguridad que no cambian el contrato observable para
  un cliente que ya se comportaba correctamente**: ej. la migración del
  `refreshToken` a cookie HttpOnly (`1dfc4f8`) es PATCH/MINOR según se
  mire — en este proyecto se trató como parte del ciclo de la Tercera
  Entrega (pre-1.0, ver más abajo) porque ningún cliente real dependía
  del comportamiento anterior (verificado por grep antes del cambio, ver
  `docs/adr/adr-007-cookies-jwt.md`).
- **Correcciones de configuración/infraestructura que no afectan el
  contrato de la aplicación**: ej. pinning de imágenes Docker por digest
  (`b747ef0`), fixes de Dockerfile/healthcheck.

## Convención de pre-release

Se usa el sufijo **`-rc`** (release candidate) para versiones que ya
cumplen el alcance funcional de una entrega pero aún no se consideran
definitivas — por ejemplo, el tag objetivo de esta Tercera Entrega es
**`v0.9.0-rc`**: indica que el proyecto está en la versión `0.9.0` en
camino hacia `1.0.0` (primera versión considerada "estable" del PFC,
probablemente al cierre de la entrega final con Bloques C y D
completos), pero todavía sujeto a ajustes menores antes de esa versión
final.

Formato: `MAJOR.MINOR.PATCH-rc` (ej. `v0.9.0-rc`, y si se necesitara una
segunda vuelta antes de cerrar esa versión, `v0.9.0-rc.2`, siguiendo la
sintaxis de metadatos de pre-release que permite SemVer 2.0.0 con `.`
como separador de identificadores adicionales).

## Por qué el proyecto sigue en `0.x.y` (no `1.x.y` todavía)

SemVer 2.0.0 reserva `0.y.z` para desarrollo inicial, donde la API puede
cambiar en cualquier momento sin que un incremento de MINOR o PATCH
implique necesariamente la misma garantía de compatibilidad que aplicaría
a partir de `1.0.0`. Es el estado correcto para un proyecto académico que
todavía no ha declarado una versión pública "estable" — se reserva
`1.0.0` para cuando el alcance completo de la guía (Bloques A-E) esté
implementado y verificado.

## Historial de tags

| Tag | Corresponde a |
|---|---|
| `v0.1.0-entrega-1b` | Cierre de la Entrega 1B (commit `1f89354`) |
| `v0.7.1` | Punto intermedio previo a la Tercera Entrega, con feedback de Entregas 1A/1B incorporado (commit `464fbf7`) |
| `v0.9.0-rc` | Cierre de la Tercera Entrega (commit `c6b372c`) |
| `v1.0.0` (objetivo de la Entrega Final) | Primera versión estable del PFC — Bloques A-E completos, despliegue público con TLS, evaluación SUS con muestra completa. Pendiente de crear al finalizar la Entrega Final |

### Nota de reproducibilidad — re-archivado en Zenodo

El feedback de la Tercera Entrega señaló que el DOI de Zenodo, archivado
sobre `v0.9.0-rc`, quedó **12 commits por detrás** del commit real que
contiene el informe y las evidencias de rendimiento/accesibilidad
(`main` avanzó de `c6b372c` a `ac9b8a5` después de crear el tag, sin
re-etiquetar). Para `v1.0.0`, el tag debe crearse **sobre el commit
final verificado** (build en verde, informe y evidencia ya incluidos en
el árbol) antes de archivar en Zenodo — no re-archivar sobre un tag ya
existente que pueda haber quedado desactualizado.

## Referencias

- Semantic Versioning 2.0.0: https://semver.org/lang/es/
- `CHANGELOG.md` (historial de cambios agrupado por versión, formato Keep a Changelog)
- `CITATION.cff` (campo `version`, se mantiene sincronizado con el tag más reciente)
