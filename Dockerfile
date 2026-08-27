FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
COPY --from=build /src/target/java-quiz-backend.jar app.jar
EXPOSE 8080
# Free hosting tiers give the container 512 MB. The JVM default of 25% would leave a heap
# too small for the Flyway content migration.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
