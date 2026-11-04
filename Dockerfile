FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package

FROM eclipse-temurin:17-jre

WORKDIR /app
RUN useradd --system --uid 10001 spring
COPY --from=build /workspace/target/heronqueue-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Manila", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
