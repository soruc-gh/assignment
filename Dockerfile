# syntax=docker/dockerfile:1

# Build stage: compile and package the boot jar using the committed Gradle wrapper.
# Nothing but Docker is needed on the host — no local Java or Gradle.
FROM amazoncorretto:25-alpine AS build
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon --version
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# Runtime stage: thin Amazon Corretto base + the executable jar.
FROM amazoncorretto:25-alpine AS run
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
