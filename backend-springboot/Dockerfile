# Build context = raíz del repo (ver docker-compose.yml: build.context: .)
# para poder copiar database/migrations/ además del código del backend.
# Fuente única de Flyway: database/migrations/ ya no se duplica dentro de
# src/main/resources/ (ver application.yml + SPRING_FLYWAY_LOCATIONS en
# docker-compose.yml).

# TODO(entrega-final): pinar por digest sha256 antes de la entrega final.
# Ver docs/DIGESTS-PENDIENTES.md para el comando exacto de obtencion.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY backend-springboot/mvnw backend-springboot/mvnw.cmd ./
COPY backend-springboot/.mvn/ .mvn/
COPY backend-springboot/pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY backend-springboot/src/ src/
RUN ./mvnw clean package -DskipTests -B

# TODO(entrega-final): pinar por digest sha256 antes de la entrega final.
# Ver docs/DIGESTS-PENDIENTES.md para el comando exacto de obtencion.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar
COPY database/migrations database/migrations

EXPOSE 8080

RUN apk add --no-cache curl

ENTRYPOINT ["java", "-jar", "app.jar"]
