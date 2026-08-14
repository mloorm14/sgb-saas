# BACKUP — Respaldo y restauración de datos SGB-SaaS

Estrategia de respaldo de la base de datos de producción (Neon Postgres,
plan Free) y requisitos de retención para la Entrega Final.

## 1. Estrategia de respaldo

La base de datos es **Postgres gestionado por Neon**: no hay backup
manual ni scripts de dump en el repositorio. Neon mantiene la retención
automática por dos mecanismos nativos:

1. **Point-in-time restore (PITR) / instant restore:** Neon conserva un
   *historial de cambios* de la rama raíz y permite reconstruir el estado
   de la base en cualquier instante dentro de esa ventana. Es el método
   principal de restauración (ver [RUNBOOK.md](RUNBOOK.md) §4).
2. **Branching:** se puede crear una rama (child branch) desde la rama
   raíz en el punto en el tiempo deseado y leerla/verificarla sin tocar el
   estado actual de producción.

### 1.1 Límites del plan Free de Neon (vigencia verificada: agosto 2026)

Confirmado contra la documentación oficial de Neon
(`neon.com/docs/introduction/plans`):

- **Ventana de historial (PITR): 6 horas**, con tope de **1 GB de
  cambios** acumulados, **sin cargo**. La ventana NO es configurable en el
  plan Free (en Launch/Scale es de hasta 7/30 días, facturada aparte).
- **Snapshots manuales: 1 permitido** en el plan Free (los backups
  programados no existen en Free). El almacenamiento de snapshots se
  factura aparte ($0.09/GB-mes) — para este proyecto académico el snapshot
  manual se usa como evidencia puntual, no como rutina.
- Cómputo: 100 CU-horas/mes (Neon lo duplicó de 50 a 100 en octubre
  2025); escala a cero a los 5 min de inactividad.
- Almacenamiento: 0.5 GB.

**Implicación práctica:** con la ventana PITR de solo 6 horas, el respaldo
de largo plazo NO se basa en PITR: se basa en (a) mantener los servicios
vivos durante todo el periodo de retención exigido (el dato sigue en la
base, nada se borra) y (b) el snapshot manual como garantía puntual
opcional antes de la defensa. Si se quisiera retención de días/meses
automatizada, sería necesario migrar a un plan de pago de Neon — fuera de
alcance de este proyecto.

## 2. Retención mínima obligatoria

- **Fecha de defensa:** 17 de agosto de 2026.
- **Retención exigida:** 30 días posteriores a la defensa →
  **mínimo hasta el 16 de septiembre de 2026** (2026-08-17 + 30 días).

Hasta esa fecha:

- **Render:** NO suspender, cancelar ni eliminar ninguno de los dos
  servicios (Web Service del backend y Static Site del frontend). El plan
  Free suspende solo por inactividad (backend) — eso es normal y no
  afecta la retención; lo que no puede pasar es borrar los servicios ni
  sus variables de entorno.
- **Neon:** NO eliminar el proyecto ni la base (no hay exportación
  equivalente al dato original en otro lado; la rama raíz ES el dato). Si
  el equipo quiere una red de seguridad extra, crear el **snapshot manual
  único** del plan Free en esa ventana.
- **Upstash:** NO eliminar la base de datos. Su contenido (blacklist de
  tokens y contadores de rate limit) es recreable, pero su eliminación
  rompería la operación del backend durante el periodo de evaluación.
- Verificación de vencimiento del periodo: pasado el 2026-09-16 se puede
  decidir libremente el desmantelamiento (siguiendo primero la prueba de
  restauración de §3 si hubiera quedado pendiente).

## 3. Procedimiento de prueba de restauración (evidencia real)

Objetivo: demostrar que el mecanismo de respaldo funciona **de verdad**
(no asumirlo), generando evidencia reproducible delante del tribunal.

### 3.1 Prerrequisitos

- Acceso al proyecto Neon de producción (dashboard) y a la URL de
  conexión de la rama raíz.
- Datos verificables: anotar antes de la prueba un par de valores de
  control (p. ej. el total de filas de `libro` o un registro creado hace
  unos minutos) y la marca de tiempo de un cambio reciente conocido.

### 3.2 Pasos

1. **Crear un cambio conocido:** insertar un registro marcador (o usar
   uno reciente) y anotar su `created_at` / timestamp del sistema.
2. **Crear el branch desde un punto anterior en el tiempo:**
   Neon dashboard → **Branches** → **Create branch** → elegir la rama
   raíz y seleccionar una **fecha/hora anterior** al cambio conocido
   (dentro de la ventana de 6 h; p. ej. 2–3 h atrás). Neon crea una
   child branch con el estado exacto de ese instante.
3. **Verificar la integridad:**
   - Conectar a la URL de conexión de la branch creada (se usa un
     cómputo nuevo que Neon levanta solo para esa rama; es de solo
     lectura salvo que se le agregue un cómputo de escritura).
   - Comprobar que el registro marcador del paso 2 **no existe** (porque
     el punto elegido es anterior) y que el resto de tablas/series están
     presentes e íntegras (p. ej. `SELECT count(*) FROM libro` coincide
     con lo esperado para esa fecha).
   - Comparar contra la rama raíz actual: ahí el registro marcador SÍ
     existe → prueba de que el PITR reconstruyó un estado distinto y
     consistente.
4. **Evidencia:** guardar capturas de pantalla del dashboard (branch
   creada, timestamp elegido) y de las dos consultas (con/sin marcador).
   Archivar en `docs/mediciones/` si el equipo lo requiere para la
   entrega.
5. **Limpiar:** borrar la branch de prueba una vez verificada (las
   branches hijas no cuentan contra el cupo de PITR, pero conviene no
   dejar ramas huérfanas).

### 3.3 Qué significa "restauración exitosa"

La prueba es exitosa si: la branch se crea desde el punto pedido, los
datos consultables son el estado **de ese instante** (sin el cambio
posterior), las consultas de integridad (conteos, FKs) no reportan
errores y se puede leerla sin afectar a producción. No se requiere
restaurar encima de la rama raíz en la prueba (eso es el paso de
recuperación real del RUNBOOK §4, que solo se ejecuta ante un incidente).

## 4. Referencias

- [RUNBOOK.md](RUNBOOK.md) §4 — recuperación ante incidente usando este mecanismo.
- [DEPLOYMENT.md](DEPLOYMENT.md) — arquitectura y límites del plan.
- Documentación oficial Neon (verificada agosto 2026): `neon.com/docs/introduction/plans` (ventana de historial, snapshots), `neon.com/docs/guides/branch-restore`.