# Digests pendientes de fijar (pinning) antes de la entrega final

Todas las imágenes base usadas en `docker-compose.yml` y en los `Dockerfile`
de `backend-springboot/` y `frontend-angular/` están referenciadas hoy por
**tag** (`postgres:16-alpine`, `redis:7-alpine`, etc.). Un tag puede apuntar a
un contenido distinto en el futuro (el proveedor puede republicar la misma
etiqueta con una imagen actualizada), lo cual rompe la reproducibilidad
bit-a-bit exigida en el Bloque B.1.

Antes de la entrega final, cada imagen debe pinarse por **digest sha256**
(`imagen@sha256:...`), que sí es inmutable.

## Procedimiento

1. Descargar la versión vigente de cada imagen (`docker pull <imagen>`).
2. Obtener su digest real con `docker inspect`.
3. Reemplazar el tag por `<imagen>@sha256:<digest>` en el archivo
   correspondiente (`docker-compose.yml` o el `Dockerfile` respectivo),
   eliminando el comentario `TODO` asociado.

## Comandos exactos por imagen

### postgres:16-alpine (docker-compose.yml, servicio `postgres`)

```bash
docker pull postgres:16-alpine
docker inspect --format='{{index .RepoDigests 0}}' postgres:16-alpine
```

### redis:7-alpine (docker-compose.yml, servicio `redis`)

```bash
docker pull redis:7-alpine
docker inspect --format='{{index .RepoDigests 0}}' redis:7-alpine
```

### eclipse-temurin:21-jdk-alpine (backend-springboot/Dockerfile, stage `build`)

```bash
docker pull eclipse-temurin:21-jdk-alpine
docker inspect --format='{{index .RepoDigests 0}}' eclipse-temurin:21-jdk-alpine
```

### eclipse-temurin:21-jre-alpine (backend-springboot/Dockerfile, imagen final)

```bash
docker pull eclipse-temurin:21-jre-alpine
docker inspect --format='{{index .RepoDigests 0}}' eclipse-temurin:21-jre-alpine
```

### node:20-alpine (frontend-angular/Dockerfile, stage `build`)

```bash
docker pull node:20-alpine
docker inspect --format='{{index .RepoDigests 0}}' node:20-alpine
```

### nginx:1.25-alpine (frontend-angular/Dockerfile, imagen final)

```bash
docker pull nginx:1.25-alpine
docker inspect --format='{{index .RepoDigests 0}}' nginx:1.25-alpine
```

## Notas

- El digest obtenido depende de la arquitectura de la máquina donde se
  ejecute el `docker pull` (amd64 vs arm64 pueden reportar digests
  distintos para el mismo tag). Ejecutar estos comandos en el mismo tipo
  de máquina que se usará para el despliegue final, o documentar cuál se usó.
- No inventar ni copiar digests de otra fuente (por ejemplo, de Docker Hub
  vía navegador) sin verificarlos localmente con `docker inspect`, ya que
  el digest debe corresponder exactamente a la imagen que se ejecuta.
- Una vez pinadas todas las imágenes, este archivo puede archivarse o
  actualizarse para reflejar los digests ya aplicados.
