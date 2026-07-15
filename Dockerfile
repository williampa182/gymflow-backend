# syntax=docker/dockerfile:1

# ── Stage 1: build ──────────────────────────────────────────────
# Compila con Maven + JDK completo. Esta capa NO va en la imagen final.
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# Copiar primero solo lo necesario para resolver dependencias — así Docker
# cachea esta capa y no vuelve a bajar todo Maven Central en cada build si
# solo cambió código fuente, no el pom.xml.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ── Stage 2: runtime ────────────────────────────────────────────
# Solo JRE (no JDK completo) — imagen final más chica y con menos
# superficie de ataque (sin compilador, sin herramientas de dev).
FROM eclipse-temurin:21-jre-jammy

# Hallazgo 4.6 del THREAT_MODEL.md (contenedor corriendo como root):
# antes no existía Dockerfile propio, así que no aplicaba. Ahora que existe,
# se crea explícitamente un usuario sin privilegios en vez de dejar el
# default (root), para limitar el daño si alguna vez se combina con otra
# vulnerabilidad (ej. deserialización insegura — ver THREAT_MODEL.md 1.2).
RUN groupadd -r gymflow && useradd -r -g gymflow -d /app gymflow

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

RUN chown -R gymflow:gymflow /app
USER gymflow

# Railway inyecta PORT dinámicamente — server.port ya lo lee vía
# ${SERVER_PORT:8080} en application.yaml, así que se sobreescribe con
# la variable de entorno real en tiempo de despliegue, no acá.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
