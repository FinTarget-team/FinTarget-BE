# FinTarget-server

## 환경변수 설정

이 프로젝트는 dotenv 라이브러리(spring-dotenv 등)를 사용하지 않습니다. `.env` 파일은 자동으로 로드되지 않으므로,
`application.yml`에서 참조하는 환경변수(`YOUTH_CENTER_API_KEY`, `YOUTH_CENTER_BASE_URL`, `KINFA_API_KEY`, `KINFA_BASE_URL` 등)는
아래 방법 중 하나로 실행 환경에 직접 주입해야 합니다.

- **IntelliJ IDEA**: Run/Debug Configurations → Environment variables 에 `.env.example`을 참고하여 값 입력
- **터미널 실행**: `export YOUTH_CENTER_API_KEY=... && ./gradlew bootRun` 과 같이 셸에서 환경변수를 export 후 실행
- **VM options**: `-DYOUTH_CENTER_API_KEY=...` 형태로 JVM 시스템 프로퍼티로 전달 가능 (Spring이 시스템 프로퍼티도 환경변수와 함께 바인딩함)

키 목록은 `.env.example` 파일을 참고하세요.

## 로컬에서 정책(Policy) 데이터 채우기

과거 `DataInitializer`가 제공하던 하드코딩 정책 시드 데이터는 온통청년/KINFA 실데이터 연동이 완료되면서 제거되었습니다.
로컬(`local` 프로필)에서는 `policy.sync.scheduler.enabled`가 기본 `false`라 스케줄러가 자동으로 도는 대신,
아래 방법으로 필요할 때 직접 실데이터를 채울 수 있습니다.

- **수동 트리거 API**: 인증된 사용자로 `POST /api/admin/policy/sync` 호출 (온통청년 + KINFA 순차 동기화, 결과를 바로 응답으로 확인 가능)
- **스케줄러 활성화**: `application.yml`의 `policy.sync.scheduler.enabled: true`로 바꾸면 매일 새벽 3시(Asia/Seoul)에 자동 동기화

두 방법 모두 `YOUTH_CENTER_API_KEY`, `KINFA_API_KEY` 등 위 환경변수가 설정되어 있어야 동작합니다.