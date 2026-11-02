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

# 컨테이너가 root가 아닌 전용 계정으로 실행되도록 한다
RUN addgroup -S app && adduser -S -G app app

COPY --from=build /workspace/app.jar app.jar

USER app

EXPOSE 8080
# 서비스 사용자가 한국에 있으므로 LocalDate.now() 등 기본 시간대를 한국 시간으로 맞춘다.
# UTC로 두면 한국 시간 0~9시 사이에 목표 D-day, 넛지의 "오늘"·"이번 달"이 하루 늦게 계산된다
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
