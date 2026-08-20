# Build context = raíz del repo (ver docker-compose.yml: build.context: .)
# para poder copiar database/migrations/ además del código del backend.
# Fuente única de Flyway: database/migrations/ ya no se duplica dentro de
# src/main/resources/ (ver application.yml + SPRING_FLYWAY_LOCATIONS en
# docker-compose.yml).

# Stage frontend: build del SPA de Angular. Desde el fix de sesión 2026-08
# el backend sirve el frontend desde el mismo origen (ver WebConfig), así
# que el jar empaqueta el dist de Angular en classpath:/static. Misma imagen
# node:20-alpine (y mismo digest) que usa frontend-angular/Dockerfile.
FROM node@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293 AS frontend-build
WORKDIR /fe
COPY frontend-angular/package.json frontend-angular/package-lock.json ./
RUN npm ci
COPY frontend-angular/ ./
RUN npm run build

# Pinado por digest sha256 (ver docs/DIGESTS-LOG.md): eclipse-temurin:21-jdk-alpine,
# obtenido con `docker pull` + `docker inspect` el 2026-07-22.
FROM eclipse-temurin@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS build
WORKDIR /app

COPY backend-springboot/mvnw backend-springboot/mvnw.cmd ./
COPY backend-springboot/.mvn/ .mvn/
COPY backend-springboot/pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY backend-springboot/src/ src/
# El dist del SPA entra al jar antes del package (mismo origen, ver WebConfig).
COPY --from=frontend-build /fe/dist/frontend-angular/browser/ src/main/resources/static/
RUN ./mvnw clean package -DskipTests -B

# Pinado por digest sha256 (ver docs/DIGESTS-LOG.md): eclipse-temurin:21-jre-alpine,
# obtenido con `docker pull` + `docker inspect` el 2026-07-22.
FROM eclipse-temurin@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar
COPY database/migrations database/migrations

EXPOSE 8080

RUN apk add --no-cache curl

# OWASP A05 (Bloque C.2, checklist Módulo 10.2, REQ-NF-014): la imagen
# base eclipse-temurin corre como root por defecto -- si el proceso Java
# llegara a comprometerse (deserialización, dependencia vulnerable, etc.),
# root dentro del contenedor facilita escapar o tocar el resto del
# filesystem del contenedor sin necesidad. app.jar y database/migrations
# ya fueron copiados como root antes de este punto (son read-only para el
# proceso en runtime, no necesita escribirlos), así que basta con crear el
# usuario y cambiar a él antes del ENTRYPOINT.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

ENTRYPOINT ["java", "-jar", "app.jar"]
