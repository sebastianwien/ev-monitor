FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src src
RUN ./gradlew build -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
