# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
COPY packages/observability/java packages/observability/java
COPY packages/test-support/java packages/test-support/java
COPY modules modules
COPY apps/platform-backend apps/platform-backend
RUN chmod +x mvnw \
    && ./mvnw -B -pl apps/platform-backend -am -DskipTests package \
    && cp apps/platform-backend/target/platform-backend-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --system actenora && useradd --system --gid actenora --create-home actenora
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER actenora
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
