# =============================================================================
# Salvador API (identifyr) — imagen para Azure Container Apps
# Build:  docker build -t salvador-api .
#         (o en la nube: az acr build -r <acr> -t salvador-api:<tag> .)
# =============================================================================

# ---------- 1. Compilación ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

# Primero solo los archivos de Gradle, para cachear dependencias
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && cp "$(find build/libs -name '*.jar' ! -name '*-plain.jar' | head -1)" /app.jar

# ---------- 2. Ejecución ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Agente de Application Insights: usa APPLICATIONINSIGHTS_CONNECTION_STRING
# (ya definida en la Container App) para enviar trazas, métricas y logs.
ARG APPI_AGENT_VERSION=3.6.2
ADD https://github.com/microsoft/ApplicationInsights-Java/releases/download/${APPI_AGENT_VERSION}/applicationinsights-agent-${APPI_AGENT_VERSION}.jar /app/applicationinsights-agent.jar

COPY --from=build /app.jar /app/app.jar

RUN useradd --system --uid 10001 spring \
 && chmod 644 /app/*.jar
USER 10001

ENV TZ=America/Lima \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -javaagent:/app/applicationinsights-agent.jar"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
