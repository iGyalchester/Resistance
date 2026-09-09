# Generic image for any Spring Boot module in this monorepo.
# Build from the repository root, e.g.:
#   docker build -f infrastructure/docker/Dockerfile \
#     --build-arg MODULE=services/mvc-service -t resistance/mvc-service .
ARG JAVA_VERSION=26

FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:${JAVA_VERSION}-jre
ARG MODULE
COPY --from=build /workspace/${MODULE}/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
