# ─── Build stage ────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 1. Copy pom and pre-download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Runtime stage ──────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# Non-root user
RUN useradd -r -u 1001 -m appuser

# Copy the built jar, chown to appuser
COPY --from=builder --chown=appuser:appuser \
     /app/target/rentrix-server-*.jar app.jar

USER appuser

EXPOSE 8080

# JVM flags tuned for small containers (Render free tier)
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75", \
            "-XX:+UseSerialGC", \
            "-jar", "app.jar"]