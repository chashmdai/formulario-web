# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim@sha256:c86ecead9e55a5210ac1727de38a4ca840bf03dcaf34dc9749addbf383b1920c AS frontend
WORKDIR /build/formulario-publico/front
COPY formulario-publico/front/package*.json ./
RUN npm ci
COPY formulario-publico/front/ ./
COPY contratos/fixtures/ /build/contratos/fixtures/
ENV NODE_OPTIONS="--max-old-space-size=512"
RUN npm run lint && npm test -- --maxWorkers=2 && npm run build

FROM maven:3.9.15-eclipse-temurin-25@sha256:1c3a703ab39fee7ac0880f46e6ccd22c0d701f17f0616e6e66a258ddc1c637d2 AS backend
WORKDIR /build/formulario-publico/backend
COPY formulario-publico/backend/pom.xml ./
ENV MAVEN_OPTS="-Xmx384m"
RUN mvn -B -ntp dependency:go-offline
COPY formulario-publico/backend/src ./src
COPY contratos/fixtures/ /build/contratos/fixtures/
COPY formulario-publico/front/src/formulario/territorio.json formulario-publico/front/src/formulario/paises.json /build/formulario-publico/front/src/formulario/
COPY --from=frontend /build/formulario-publico/front/dist /build/formulario-publico/front/dist
COPY --from=frontend /build/formulario-publico/front/src/prototipo/assets/logo.svg /build/formulario-publico/front/src/prototipo/assets/logo.svg
RUN mvn -B -ntp -Dtest="*Test,!Carga*Test" -DargLine=-Xmx384m verify \
    && jar tf target/formulario-publico-0.1.0-SNAPSHOT.jar > /tmp/jar-entries \
    && grep -qx "BOOT-INF/classes/catalogos/territorio.json" /tmp/jar-entries \
    && grep -qx "BOOT-INF/classes/catalogos/paises.json" /tmp/jar-entries \
    && grep -qx "BOOT-INF/classes/static/index.html" /tmp/jar-entries

FROM eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0 AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.source="https://github.com/chashmdai/formulario-web" \
      org.opencontainers.image.revision=$SOURCE_REVISION
WORKDIR /app
COPY --from=backend --chown=1000:1000 /build/formulario-publico/backend/target/formulario-publico-0.1.0-SNAPSHOT.jar /app/formulario.jar
USER 1000:1000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/formulario.jar"]
