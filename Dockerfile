# =============================================================================
# Salvador API (identifyr) — imagen para Azure Container Apps
# Build:  docker build -t salvador-api .
#         (o en la nube: az acr build -r <acr> -t salvador-api:<tag> .)
#
# Arranque en frío optimizado (la API escala a cero en Azure):
#   - jar extraído (java -Djarmode=tools extract): carga más rápida que el jar anidado
#   - archivo CDS (Class Data Sharing) generado en el build con una corrida de
#     entrenamiento sin base de datos; la JVM arranca con las clases ya pre-procesadas
#   - COPY --chmod en vez de RUN chmod: evita duplicar las capas de los jars
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

# Jar extraído (el destino debe estar vacío: va antes que el agente): /app/app.jar + /app/lib/*.jar
COPY --from=build /app.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar

# Agente de Application Insights: usa APPLICATIONINSIGHTS_CONNECTION_STRING
# (ya definida en la Container App) para enviar trazas, métricas y logs.
ARG APPI_AGENT_VERSION=3.6.2
ADD --chmod=644 https://github.com/microsoft/ApplicationInsights-Java/releases/download/${APPI_AGENT_VERSION}/applicationinsights-agent-${APPI_AGENT_VERSION}.jar /app/applicationinsights-agent.jar

# El agente lee esta configuración de la carpeta de su jar: enmascara el celular de las URLs
COPY --chmod=644 deploy/applicationinsights.json /app/applicationinsights.json

# Corrida de entrenamiento para el archivo CDS. No hay base de datos en el build:
# Flyway apagado, Hibernate sin validar ni consultar metadatos, Hikari sin conectar.
# El contexto arranca completo (Tomcat, Security, JPA) y sale al terminar de refrescar.
# JWT_SECRET es un valor de relleno solo para esta corrida; no se guarda en la imagen final.
RUN JWT_SECRET=entrenamiento-cds-no-es-un-secreto-0000000000000000000000000000 \
    java -XX:ArchiveClassesAtExit=/app/app.jsa \
         -Dspring.context.exit=onRefresh \
         -Dspring.profiles.active=azure \
         -Dspring.flyway.enabled=false \
         -Dspring.jpa.hibernate.ddl-auto=none \
         -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
         -Dspring.datasource.hikari.initialization-fail-timeout=-1 \
         -XX:TieredStopAtLevel=1 -XX:+UseSerialGC \
         -jar /app/app.jar

RUN useradd --system --uid 10001 spring
USER 10001

# Flags pensados para arranque rápido con poca CPU (0,5 vCPU en Azure):
#   TieredStopAtLevel=1  solo compilador C1: arranca mucho antes, rendimiento pico algo menor (sobra para esta API)
#   UseSerialGC          recolector simple, el más liviano para 1 réplica de 1 GB
#   SharedArchiveFile    archivo CDS generado arriba
# Para activar el agente de Application Insights, definir en la Container App la variable
#   JAVA_TOOL_OPTIONS="<estos mismos flags> -javaagent:/app/applicationinsights-agent.jar"
# (el agente alarga el arranque ~30 %; el archivo CDS se entrenó sin él).
ENV TZ=America/Lima \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:SharedArchiveFile=/app/app.jsa -Xlog:cds=off"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
