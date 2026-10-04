# Build stage - compiles the application
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src src
RUN ./gradlew build -x test

# Development stage - for local dev with hot reload
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon
EXPOSE 8080
CMD ["./gradlew", "bootRun"]

# Runtime-Basis - minimal JRE
FROM eclipse-temurin:21-jre-jammy AS runtime
# Java2D braucht installierte Schriften - das JRE-Image bringt keine mit.
# Ohne sie faellt die Textausgabe im Vorschaubild geteilter Ladekurven aus
# (PowerCurveImageRenderer). Lokal auf macOS faellt das nicht auf, weil das
# System dort eigene Fonts stellt.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# CI: das im Testlauf gebaute Jar (build/app.jar) statt erneut zu kompilieren.
# Aufruf: docker build --target prebuilt
FROM runtime AS prebuilt
COPY build/app.jar app.jar

# Production stage (Default) - kompiliert selbst, fuer lokalen Bau und Community-Setup
FROM runtime
COPY --from=builder /app/build/libs/*.jar app.jar
