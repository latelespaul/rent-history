# =============================================================================
# Rentrix Server — Multi-stage Dockerfile
# =============================================================================

# --- Build stage ------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first so dependency download can be cached
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# --- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Non-root user (uid 1001 — matches common container conventions)
RUN useradd -r -u 1001 -m appuser

# Copy only the fat jar
COPY --from=builder --chown=appuser:appuser \
     /app/target/rentrix-server-*.jar app.jar

USER appuser

EXPOSE 8080

# JVM flags tuned for small containers (Render free tier)
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=70", \
            "-XX:InitialRAMPercentage=50", \
            "-XX:+UseSerialGC", \
            "-XX:MaxMetaspaceSize=128m", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "app.jar"]