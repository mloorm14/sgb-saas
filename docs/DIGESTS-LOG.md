# Registro de digests sha256 aplicados

Este archivo reemplaza a `docs/DIGESTS-PENDIENTES.md` (eliminado): su
propósito — pinar por digest todas las imágenes base usadas en
`docker-compose.yml` y en los `Dockerfile` de `backend-springboot/` y
`frontend-angular/` — ya se cumplió. Se conserva como referencia histórica
de cuándo y con qué comando se fijó cada versión, en caso de que en el
futuro haga falta actualizar alguna imagen base deliberadamente.

## Fecha de anclaje

**2026-07-22**, obtenidos localmente con:
```bash
docker pull <imagen>
docker inspect --format='{{index .RepoDigests 0}}' <imagen>
```

Arquitectura de la máquina donde se obtuvieron: la usada para el
desarrollo/build de este proyecto (ver nota de `docs/DIGESTS-PENDIENTES.md`
original: el digest puede variar entre amd64/arm64 para el mismo tag).

## Digests aplicados

| Imagen (tag original)        | Usado en                                                  | Digest aplicado |
|-------------------------------|------------------------------------------------------------|------------------|
| `postgres:16-alpine`          | `docker-compose.yml` (servicio `postgres`)                 | `postgres@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` |
| `redis:7-alpine`               | `docker-compose.yml` (servicio `redis`)                    | `redis@sha256:6ab0b6e7381779332f97b8ca76193e45b0756f38d4c0dcda72dbb3c32061ab99` |
| `eclipse-temurin:21-jdk-alpine`| `backend-springboot/Dockerfile` (stage `build`)             | `eclipse-temurin@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76` |
| `eclipse-temurin:21-jre-alpine`| `backend-springboot/Dockerfile` (imagen final)              | `eclipse-temurin@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c` |
| `node:20-alpine`               | `frontend-angular/Dockerfile` (stage `build`)               | `node@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293` |
| `nginx:1.25-alpine`            | `frontend-angular/Dockerfile` (imagen final)                | `nginx@sha256:516475cc129da42866742567714ddc681e5eed7b9ee0b9e9c015e464b4221a00` |

## Verificación realizada

Tras aplicar los 6 pins: `docker compose build` (backend y frontend
compilan sin error) y `docker compose up -d` completo desde cero
(`postgres`, `redis`, `backend` reportan `healthy`; `backend` responde
`200` en `/actuator/health`). El healthcheck intermitente de `frontend` es
un problema preexistente ya documentado en sesiones anteriores, ajeno a
este cambio (módulo de Panama, fuera de alcance).

## Cómo actualizar un digest en el futuro

Si alguna imagen base necesita actualizarse deliberadamente (parche de
seguridad del proveedor, nueva versión menor, etc.):

1. `docker pull <imagen>:<tag-deseado>`
2. `docker inspect --format='{{index .RepoDigests 0}}' <imagen>:<tag-deseado>`
3. Reemplazar el digest en el archivo correspondiente y en la tabla de
   arriba, actualizando también la fecha de esta sección.
4. Repetir la verificación (`docker compose build` + `docker compose up -d`)
   antes de dar por buena la actualización.
