---
name: sgb-flyway-migrations
description: Migraciones de base de datos con Flyway en SGB-SaaS — naming, estructura, stored procedures, y seed data. Usar SIEMPRE al crear una nueva migración SQL, al modificar el schema de PostgreSQL, o al agregar stored procedures — cubre el patrón de naming, las migraciones existentes, y cómo inicializar la BD para tests y Docker.
---

# Migraciones Flyway — SGB-SaaS

## Estructura del directorio

```
database/
├── migrations/
│   ├── V1__schema_inicial.sql
│   ├── V2__seed_roles.sql
│   ├── ...
│   └── V13__portada_imagen.sql
├── seed/
│   └── (datos iniciales)
db/
├── schema/
│   └── (schema completo generado)
├── stored-procedures/
│   └── (funciones PL/pgSQL)
└── init.sql
```

## Naming de migraciones

```
V{N}__{descripcion_en_snake_case}.sql
```

- `V` mayúscula + número de versión secuencial
- `__` (doble guion bajo) como separador
- Descripción en snake_case, sin espacios, sin caracteres especiales
- Ejemplo: `V14__agregar_campo_telefono.sql`

**REGLAS:**
- NUNCA reusar un número de versión ya existente
- NUNCA modificar una migración que ya fue aplicada (ya está en la BD)
- Si necesitás corregir algo de una migración anterior, crear una nueva migración

## Configuración de Flyway

En `application.yml`:
```yaml
spring:
  flyway:
    baseline-version: 14
    baseline-on-migrate: true
```

Esto significa que Flyway ya considera aplicadas las migraciones V1-V14
(existentes en `conf-producción`). Si estás creando una migración nueva,
el siguiente número válido es **V27** (V24, V25, V26 ya están tomados en
`demo/interfaces-completas`).

## Migraciones vs Repeatable

| Tipo | Prefijo | Cuándo usar |
|------|---------|-------------|
| Versioned | `V{N}__` | Cambios de schema (ALTER, CREATE TABLE, etc.) |
| Repeatable | `R__` | Stored procedures, vistas, funciones que pueden re-ejecutarse |

Los `R__` se re-ejecutan automáticamente si su contenido cambia. Usarlos
para `R__stored_procedures.sql` que contiene todos los SPs del proyecto.

## Bug conocido: duplicado de versión V24

En `conf-producción` existía `V24__add_precio_to_tipos_dano.sql` y en
`demo/interfaces-completas` se creó otro `V24__libro_ajustes_biblioteca.sql`.
Flyway falla con "Validate failed: Migrations have failed validation"
cuando hay duplicados. **Solución:** al crear una migración, SIEMPRE
verificar que el número no existe en NINGUNA rama:

```bash
# Verificar en todas las ramas
git ls-tree -r --name-only HEAD database/migrations/ | sort -V
git ls-tree -r --name-only origin/conf-produccion database/migrations/ | sort -V
```

## Migraciones existentes

| Versión | Archivo | Propósito |
|---------|---------|-----------|
| V1 | schema_inicial | Tablas base (usuarios, libros, etc.) |
| V2 | seed_roles | Roles: LECTOR, BIBLIOTECARIO, GERENTE, ADMIN |
| V3-V12 | (varias) | Funcionalidades incrementales |
| V13 | portada_imagen | Columnas BYTEA para portadas de libros |
| V14 | seed_configuracion | Datos iniciales de configuración_sistema |
| V15-V23 | (varias) | Funcionalidades incrementales |
| V24 | add_precio_to_tipos_dano | Precio en tipos de daño (conf-producción) |
| V25 | categoria_y_tipo_costo_dano | Categoría y tipo de costo en daños |
| V26 | precio_base_nullable_para_bibliotecario | Precio base nullable para bibliotecario |
| V27 | libro_ajustes_biblioteca | Ajustes de biblioteca (campos libro) |

## Patrón de una migración típica

```sql
-- V14__agregar_campo_telefono.sql
ALTER TABLE usuarios ADD COLUMN telefono VARCHAR(20);
ALTER TABLE usuarios ADD COLUMN telefono_verificado BOOLEAN DEFAULT FALSE;
```

## Patrón con stored procedure

```sql
-- V15__fn_registrar_prestamo.sql
CREATE OR REPLACE FUNCTION fn_registrar_prestamo(
    p_usuario_id BIGINT,
    p_libro_id BIGINT,
    p_fecha_devolucion_esperada DATE
) RETURNS BIGINT AS $$
DECLARE
    v_prestamo_id BIGINT;
    v_ejemplares_disponibles INT;
BEGIN
    -- Verificar disponibilidad
    SELECT ejemplares_disponibles INTO v_ejemplares_disponibles
    FROM libros WHERE id = p_libro_id;

    IF v_ejemplares_disponibles <= 0 THEN
        RAISE EXCEPTION 'No hay ejemplares disponibles';
    END IF;

    -- Crear préstamo
    INSERT INTO prestamos (usuario_id, libro_id, fecha_prestamo, fecha_devolucion_esperada, estado)
    VALUES (p_usuario_id, p_libro_id, CURRENT_DATE, p_fecha_devolucion_esperada, 'ACTIVO')
    RETURNING id INTO v_prestamo_id;

    -- Actualizar stock
    UPDATE libros SET ejemplares_disponibles = ejemplares_disponibles - 1
    WHERE id = p_libro_id;

    RETURN v_prestamo_id;
END;
$$ LANGUAGE plpgsql;
```

## Patrón con seed data

```sql
-- V16__seed_configuracion_inicial.sql
INSERT INTO configuracion_sistema (clave, valor, descripcion)
VALUES
    ('max_tamano_portada_mb', '2', 'Tamaño máximo de portada en MB'),
    ('dias_prestamo_default', '14', 'Días de préstamo por defecto'),
    ('max_reservaciones_por_usuario', '3', 'Máximo de reservaciones activas por usuario')
ON CONFLICT (clave) DO NOTHING;
```

## Inicialización de BD para tests/Docker

El script `scripts/build-init-sql.sh` genera el schema completo:

```bash
# Genera db/schema/schema.sql y db/stored-procedures/
make init-db  # o directamente:
./scripts/build-init-sql.sh
```

En Docker, el `docker-compose.yml` ejecuta este script al levantar PostgreSQL:

```yaml
postgres:
  volumes:
    - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql
```

## Reglas

- NUNCA hagas `DROP TABLE` en una migración — usa `ALTER TABLE` para modificar
- NUNCA hardcodees IDs en migraciones — usa secuencias o `RETURNING id`
- NUNCA modifiques una migración que ya fue aplicada (Flyway la marca como ejecutada)
- Siempre usá `IF NOT EXISTS` / `IF EXISTS` para idempotencia cuando sea posible
- Los stored procedures van en la migración directamente (no en archivos separados)
- El seed data debe ser idempotente (`ON CONFLICT DO NOTHING`)
- Antes de crear una migración, verificá cuál es la última versión: `ls database/migrations/ | sort -V | tail -1`
