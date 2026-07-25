# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY packages/observability/java packages/observability/java
COPY packages/test-support/java packages/test-support/java
COPY modules modules
COPY apps/platform-backend apps/platform-backend
RUN mvn -B -pl apps/platform-backend -am -DskipTests package \
    && cp apps/platform-backend/target/platform-backend-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system actenora \
    && useradd --system --gid actenora --create-home actenora
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER actenora
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    SPRING_PROFILES_ACTIVE=local \
    ACTENORA_ENV=local
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=12 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness | grep -q UP
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
