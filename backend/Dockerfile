# ============================================================================
# CRM Backend — Production multi-stage Dockerfile
# Java 21 + Spring Boot
# ============================================================================

# ---------------------------------------------------------------------------
# Stage 1 — Dependency cache
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS dependencies

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw

# Cache Maven dependencies
RUN ./mvnw dependency:go-offline -B


# ---------------------------------------------------------------------------
# Stage 2 — Build application
# ---------------------------------------------------------------------------
FROM dependencies AS build

WORKDIR /build

COPY src/ src/

RUN ./mvnw clean package \
    -B \
    -DskipTests \
    -Dspotless.check.skip=true \
    -Dspotless.apply.skip=true

# Copy the executable Spring Boot JAR under a predictable name.
# Excludes Maven's *.original backup file.
RUN JAR_FILE="$(find target \
    -maxdepth 1 \
    -type f \
    -name '*.jar' \
    ! -name '*.original' \
    ! -name '*-sources.jar' \
    ! -name '*-javadoc.jar' \
    | head -n 1)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /build/app.jar


# ---------------------------------------------------------------------------
# Stage 3 — Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL org.opencontainers.image.title="CRM Backend" \
    org.opencontainers.image.description="Multi-tenant CRM Spring Boot backend" \
    org.opencontainers.image.vendor="SellSpark"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 crm \
    && useradd \
    --system \
    --uid 1001 \
    --gid crm \
    --create-home \
    --shell /usr/sbin/nologin \
    crm

WORKDIR /app

COPY --from=build --chown=crm:crm /build/app.jar /app/app.jar

RUN mkdir -p /app/logs /app/uploads \
    && chown -R crm:crm /app

USER crm

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=25.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC"

EXPOSE 8080

HEALTHCHECK \
    --interval=30s \
    --timeout=10s \
    --start-period=90s \
    --retries=5 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]