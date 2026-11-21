# =============================================================================
# Rentrix API image: multi-stage Maven build, then a small JRE 21 runtime.
#   docker build -t rentrix-server rentrix-server
# Builds with a plain (non-BuildKit) docker build, as Render does: no syntax line,
# no cache mounts. Runs as a non-root user. SQLite data and local media live under /app/data;
# mount a volume there (docker-compose.yml does). Config is env only (§17).
# =============================================================================

# --- Build stage ------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Resolve dependencies first so this layer stays cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests \
 && cp target/rentrix-server-*.jar /build/app.jar

# --- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre

# curl is only for the container health check.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin rentrix \
 && mkdir -p /app/data/media \
 && chown -R rentrix:rentrix /app

WORKDIR /app

COPY --from=builder --chown=rentrix:rentrix /build/app.jar app.jar

USER rentrix

# Defaults for a single-box container (D-09, D-11). Every one can be overridden.
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    DB_URL=jdbc:sqlite:/app/data/rentrix.db \
    STORAGE_LOCAL_DIR=/app/data/media

VOLUME ["/app/data"]
EXPOSE 8080

# Readiness includes the database (§12 Ops); the API lives under /api/v1.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/api/v1/actuator/health/readiness" > /dev/null || exit 1

ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75", \
            "-XX:+UseSerialGC", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "/app/app.jar"]
