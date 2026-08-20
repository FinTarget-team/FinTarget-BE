# syntax=docker/dockerfile:1

# ---- 1단계: 빌드 ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src
RUN ./gradlew bootJar --no-daemon -x test \
    && cp $(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar') app.jar

# ---- 2단계: 실행 ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
