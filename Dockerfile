FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
RUN addgroup -S healthcare && adduser -S -G healthcare healthcare
COPY --from=build /workspace/target/healthcare-resource-allocation-0.0.1-SNAPSHOT.jar /app/app.jar
USER healthcare
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
