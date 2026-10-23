# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS frontend
WORKDIR /build/front
COPY formulario-publico/front/package*.json ./
RUN npm ci
COPY formulario-publico/front/ ./
RUN npm run lint && npm test && npm run build

FROM maven:3.9.15-eclipse-temurin-25 AS backend
WORKDIR /build/backend
COPY formulario-publico/backend/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY formulario-publico/backend/src ./src
COPY --from=frontend /build/front/dist /build/front/dist
RUN mvn -B -ntp verify

FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=backend --chown=1000:1000 /build/backend/target/formulario-publico-0.1.0-SNAPSHOT.jar /app/formulario.jar
USER 1000:1000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/formulario.jar"]
