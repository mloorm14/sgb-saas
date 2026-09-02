# Evidencia — Verificación en runtime de los 3 procedimientos multi-OUT (`sp_registrar_devolucion`, `sp_pagar_multa`, `sp_anular_multa`)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-29T22:18:27Z
- **Commit**: `85f9ea8`
- **Docker**: Docker version 29.3.1, build c2be9cc
- **Docker Compose**: Docker Compose version v5.1.1
- **Java**: java version "21.0.10" 2026-01-20 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Propósito

Cerrar el seguimiento obligatorio dejado en `docs/basedatos/CATALOGO-SP.md`
("Pendiente (seguimiento obligatorio para el prompt de Cajas)"): los 3
procedimientos con múltiples parámetros OUT (`sp_registrar_devolucion`,
`sp_pagar_multa`, `sp_anular_multa`) compilaban vía
`@NamedStoredProcedureQuery` pero nunca se habían ejecutado en runtime
contra Postgres real — combinación conocida como frágil entre
Hibernate/pgjdbc y procedimientos con múltiples OUT.

Al ejecutarlos por primera vez (`PrestamoMultaProcedureIntegrationTest`)
se confirmó el fallo real en runtime, lo que motivó migrar los 5 métodos
afectados de `@Procedure`/`@NamedStoredProcedureQuery` a
`@Query(nativeQuery = true)` en `PrestamoProcedureRepository`,
`MultaProcedureRepository`, `ReservacionProcedureRepository` (commits
`bce5ef9`, `16ec788`, `ba35f7a`, `dfdce0f`, `2133bbd`). Esta evidencia
documenta la corrida que confirma que, tras el fix, los 3 procedimientos
se invocan correctamente de punta a punta. Relacionado con `ADR-006`.

## Metodología / comando ejecutado

Con el stack de base de datos levantado:

```bash
docker compose up -d postgres redis
```

Y desde `backend-springboot/`:

```bash
./mvnw test -Dtest=PrestamoMultaProcedureIntegrationTest
```

`PrestamoMultaProcedureIntegrationTest` es un `@SpringBootTest` (sin
mocks) que se conecta a la instancia real de Postgres del contenedor
`sgb_postgres` (`localhost:5432/sgb_db`, configuración por defecto de
`application.yml`). La clase está anotada `@Transactional`: cada `@Test`
corre en su propia transacción, revertida automáticamente al finalizar,
por lo que no deja datos residuales en la base real entre corridas. Los
datos de prueba (usuario, libro) se insertan vía `JdbcTemplate` directo
para controlar columnas `NOT NULL` que ningún service expone todavía.

Cubre 6 escenarios:
1. Devolución sin atraso.
2. Devolución con atraso (genera multa `PENDIENTE`).
3. Doble devolución del mismo préstamo (`LB409`).
4. Pago de multa que desbloquea al usuario.
5. Anulación de multa con rol `GERENTE` (verifica también la fila
   generada en `bitacora_auditoria`).
6. Anulación de multa con rol inválido (`LB422`).

## Resultados crudos

Confirmado por el ejecutor de la prueba en su máquina local: la corrida
terminó con **`Tests run: 6, Failures: 0, Errors: 0`** — los 6 escenarios
pasaron tras el fix. La salida completa de consola de Maven no quedó
capturada en esta corrida puntual; el `BUILD SUCCESS` con el conteo
anterior es el dato verificado.

## Análisis breve

Confirma que el fix real (`@Procedure` → `@Query(nativeQuery = true)`)
resuelve el problema de invocación en runtime de los 3 procedimientos
multi-OUT: los 6 escenarios, incluyendo los casos de error (`LB409`,
`LB422`) y el efecto colateral en `bitacora_auditoria`, pasan contra
Postgres 16 real, no contra mocks. Esto cierra el seguimiento marcado
como obligatorio en `docs/basedatos/CATALOGO-SP.md`. No cubre: la
verificación manual end-to-end vía HTTP (`curl`) del flujo completo
préstamo → devolución con atraso → multa → bloqueo → login 423 → pago →
desbloqueo (sección 7 de `INSTRUCCIONES.md`), que sigue pendiente como
verificación separada a nivel de API REST, no de capa de repositorio.
