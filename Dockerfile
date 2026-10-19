FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY . .
RUN sed -i 's/\r$//' gradlew && sh gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
