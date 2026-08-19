# Resumen: bug de portada al editar libros (snapshot de BD desincronizado)

## Síntoma reportado

Al editar un libro, el gerente sube una portada (JPG o PNG), el libro se
guarda con sus demás campos pero la portada "no se guarda". El frontend
muestra el mensaje genérico "El libro se guardó, pero hubo un error al
subir su portada" (sin detalle del error real).

## Diagnóstico (verificado, no supuesto)

El flujo de portada está completo y correcto en el código:

- Frontend: `libros.component.ts` (validación de tipo/tamaño, preview,
  `guardarPortadaPendiente`) y `libro.service.subirPortada()` (FormData
  campo `archivo`, sin fijar Content-Type).
- Backend: `POST/GET /api/v1/libros/{id}/portada` en `LibroController`,
  `LibroService.actualizarPortada()` con whitelist
  `TIPOS_PORTADA_PERMITIDOS` (PNG/JPEG/WEBP) y límite
  `max_tamano_portada_mb` desde `configuracion_sistema`.

El problema real estaba en la **fuente de datos de la demo**:

1. `db/schema.sql` (el snapshot que siembra la BD de la demo vía
   `docker-entrypoint-initdb.d`) estaba **desincronizado desde la V5**:
   no incluía las tablas de notificaciones/chatbot/credencial QR ni las
   columnas `portada_imagen/portada_nombre/portada_tipo/portada_tamanio`
   de `V13__portada_imagen.sql`. Una BD creada desde ese snapshot nacía
   sin las columnas de portada → el `POST /{id}/portada` fallaba con 500.
2. `db/seed.sql` no tenía `max_tamano_portada_mb` (solo existía en la
   V13) → `LibroService.validarPortada()` lanza "Clave de configuración
   no encontrada" → 500.
3. `baseline-version: 3` en `application.yml` quedó desactualizado: el
   comentario exige que sea la migración más alta reflejada en el
   snapshot.

## Arreglo aplicado (rama fix/portada-snapshot-schema)

1. **`db/schema.sql` regenerado** (`6ebeb5a`): `pg_dump --schema-only`
   real desde una base con las migraciones V1..V13 aplicadas en orden
   numérico (31 tablas, constraints, índices, extensiones `pg_trgm` y
   `uuid-ossp`). Normalizado: sin cabecera/SETs de pg_dump, encabezado
   del proyecto.
2. **`db/seed.sql`** (`77bdb64`): agregadas `max_tamano_portada_mb` (V13)
   y `minutos_reserva`, `dias_prestamo_default`, `max_renovaciones_default`
   (V4) — el snapshot debe ser autocontenido.
3. **`application.yml`** (`dc2721e`): `baseline-version: 3 → 13`.
4. `db/init/01-consolidado.sql` regenerado en disco (gitignored, se
   regenra con `make up` / `scripts/build-init-sql.sh`).

## Verificación (con PostgreSQL local del usuario)

- BD creada **solo** con `db/init/01-consolidado.sql` (31 tablas, 5
  claves de configuración) → `LibroPortadaIntegrationTest` **3/3 PASS**
  con `baseline-version: 13` (Flyway baselina, no re-aplica migraciones,
  solo el repeatable de stored procedures).
- Suite backend completa: todos los tests **PASS** (los 9 de integración
  que antes fallaban por la BD — `LibroPortadaIntegrationTest` y
  `PrestamoMultaProcedureIntegrationTest` — ahora pasan; los 2 skipped de
  `ChatbotServiceIntegrationTest` son preexistentes, requieren API key).
- Flujo real del bug: con el snapshot viejo la BD nacía sin columnas de
  portada y la subida fallaba 500; con el snapshot regenerado + baseline
  13 el flujo completo funciona.

## Pendiente para el usuario

- Su BD local existente (volumen Docker) puede haberse creado con el
  snapshot viejo: si la portada sigue fallando al levantar la demo,
  recrear el volumen (`docker compose down -v` + `make up`). Con el
  snapshot nuevo nace completa. En Render no hace falta nada: Flyway ya
  aplicó V1-V13 desde cero.
