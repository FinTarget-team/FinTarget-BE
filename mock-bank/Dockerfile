# syntax=docker/dockerfile:1
# 가상 금융 서버(mock-bank) 이미지 (#204). 메인 서버 Dockerfile과 같은 구성이며, 빌드 컨텍스트는 mock-bank/ 폴더다.

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

# 컨테이너가 root가 아닌 전용 계정으로 실행되도록 한다.
# H2 파일 DB(./data → /app/data)는 볼륨으로 유지하므로, 볼륨이 처음 만들어질 때 이 계정 소유가 되도록 폴더를 미리 만든다
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app/data && chown app:app /app/data

COPY --from=build /workspace/app.jar app.jar

USER app

EXPOSE 8081
# 결제 날짜 검증과 시연 데이터는 AppConfig의 한국 시간 Clock을 쓰지만, 로그 시각 등도 맞추기 위해 기본 시간대를 한국으로 둔다
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
