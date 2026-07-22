# ADR-012: Docker Compose como estrategia de despliegue

## Title

Docker Compose (no Kubernetes, no despliegue manual) como estrategia de
orquestación y despliegue de los 4 servicios de SGB-SaaS.

## Context

El sistema se compone de 4 servicios (`frontend`, `backend`, `postgres`,
`redis`, ver `docker-compose.yml`), construidos con Dockerfiles
multi-stage y arrancados hoy con un único comando (`make up`, que
regenera `db/init/` vía `scripts/build-init-sql.sh` y luego levanta el
stack). `ADR-001-tecnologia.md` menciona esta elección de forma breve
como parte de la pila principal; este ADR documenta el análisis completo
de alternativas.

## Decision

Se elige **Docker Compose** como mecanismo de orquestación y despliegue,
tanto para desarrollo local como para el entorno de evaluación de esta
entrega.

## Alternativas consideradas

- **Kubernetes:** descartado como sobre-ingeniería para el alcance
  actual. El caso de uso real es 4 servicios, un solo equipo de 3
  personas, sin necesidad de auto-scaling, sin múltiples nodos físicos
  que orquestar, y sin requisito de alta disponibilidad multi-región para
  un PFC académico de uso institucional de bajo volumen (ver prioridad
  "Media" de Eficiencia de desempeño en `docs/arquitectura/ISO25010.md`
  — no hay evidencia de carga que justifique la complejidad operativa
  adicional de un orquestador). Adoptar Kubernetes aquí introduciría
  curva de aprendizaje (manifests, Helm, un cluster real o local tipo
  kind/minikube) sin ningún beneficio medible sobre el problema real que
  el proyecto tiene hoy.
- **Despliegue manual (instalar cada servicio directamente en el host,
  sin contenedores):** descartado. Rompe la reproducibilidad exigida por
  el Bloque B de esta entrega — un evaluador necesita levantar el
  sistema completo en su propia máquina sin depender de que tenga
  Java 21, Node 20, PostgreSQL 16 y Redis 7 instalados y configurados
  exactamente igual que el entorno del equipo. También pierde el
  aislamiento de versiones que dan las imágenes pinadas por digest
  (`docs/DIGESTS-LOG.md`).
- **Docker Compose (elegido):** da reproducibilidad de un solo comando
  (`make up`) sin la complejidad operativa de un orquestador pensado
  para escalado multi-nodo. Cubre exactamente el caso de uso real:
  levantar 4 servicios con dependencias de arranque ordenadas
  (`depends_on: condition: service_healthy`) en una sola máquina.

## Detalles de implementación relevantes a la decisión

- **Builds multi-stage:** tanto `backend-springboot/Dockerfile` (stage de
  build con Maven sobre `eclipse-temurin:21-jdk-alpine`, stage final solo
  con el JRE) como `frontend-angular/Dockerfile` (stage de build con
  `npm run build` sobre `node:20-alpine`, stage final sirviendo los
  estáticos con `nginx:1.25-alpine`) siguen el patrón multi-stage: la
  imagen final no carga el JDK completo ni las `devDependencies` de
  Node, solo el artefacto ya construido — reduce superficie de ataque y
  tamaño de imagen sin sacrificar reproducibilidad del build.
- **Imágenes pinadas por digest sha256** (no por tag flotante como
  `postgres:16-alpine`, que puede apuntar a contenido distinto en el
  futuro): las 6 imágenes base del stack están pinadas, documentadas en
  `docs/DIGESTS-LOG.md`, obtenidas con `docker pull` + `docker inspect`.
- **Variables de entorno vía `.env.example`:** ningún secreto ni valor
  de configuración por entorno (credenciales de BD, `JWT_SECRET`, TTL de
  cache) está hardcodeado en `docker-compose.yml` — todos se resuelven
  vía `${VAR}` desde un `.env` que cada entorno provee, con
  `.env.example` documentando cada variable y su propósito sin exponer
  valores reales.
- **Healthchecks y orden de arranque:** `depends_on: condition:
  service_healthy` en `backend` (espera a `postgres` y `redis` sanos) y
  en `frontend` (espera a `backend` sano) evita condiciones de carrera
  típicas de Compose sin healthchecks, donde un servicio arranca antes
  de que su dependencia esté realmente lista para aceptar conexiones.

## Status

Aceptado e implementado. Verificado en vivo repetidamente durante esta
entrega: `docker compose down -v` + `make up` reconstruye el stack
completo desde cero (volumen vacío) de forma reproducible.

## Consequences

**Positivas:**

- Reproducibilidad de un solo comando, requisito explícito del Bloque B.
- Ningún secreto committeado al repositorio (`.env` está gitignored,
  `.env.example` documenta la forma sin exponer valores reales).
- Superficie de ataque reducida por los builds multi-stage (imágenes
  finales sin herramientas de build).

**Negativas:**

- No hay auto-scaling ni recuperación automática ante caída de un nodo
  físico completo — aceptado porque el proyecto corre en una sola
  máquina, no en un cluster.
- Migrar a un orquestador más adelante (si el proyecto creciera más allá
  del alcance académico) requeriría reescribir `docker-compose.yml` como
  manifests de Kubernetes o Helm charts — coste diferido
  deliberadamente, no ignorado.

## Referencias

- [[ADR-001-tecnologia]] (elección de la pila principal; remite aquí para el detalle)
- `docker-compose.yml`, `backend-springboot/Dockerfile`, `frontend-angular/Dockerfile`
- `docs/DIGESTS-LOG.md` (digests sha256 de las 6 imágenes base)
- `.env.example`, `Makefile` (`make up`)
