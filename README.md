# FinTarget-server

## 환경변수 설정

이 프로젝트는 dotenv 라이브러리(spring-dotenv 등)를 사용하지 않습니다. `.env` 파일은 자동으로 로드되지 않으므로,
`application.yml`에서 참조하는 환경변수(`YOUTH_CENTER_API_KEY`, `YOUTH_CENTER_BASE_URL`, `KINFA_API_KEY`, `KINFA_BASE_URL` 등)는
아래 방법 중 하나로 실행 환경에 직접 주입해야 합니다.

- **IntelliJ IDEA**: Run/Debug Configurations → Environment variables 에 `.env.example`을 참고하여 값 입력
- **터미널 실행**: `export YOUTH_CENTER_API_KEY=... && ./gradlew bootRun` 과 같이 셸에서 환경변수를 export 후 실행
- **VM options**: `-DYOUTH_CENTER_API_KEY=...` 형태로 JVM 시스템 프로퍼티로 전달 가능 (Spring이 시스템 프로퍼티도 환경변수와 함께 바인딩함)

키 목록은 `.env.example` 파일을 참고하세요.