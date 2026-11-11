# Fin-Target Convention

## 작업 순서

1. 이슈 생성
2. 이슈 기반 브랜치 생성
3. 작업 및 커밋
4. dev 브랜치로 PR 생성
5. 리뷰 후 Squash and merge
6. 브랜치 삭제

---

## 이슈

제목은 `[타입] 작업 내용` 형식으로 작성
[Feat] 카카오 로그인 구현
[Fix] 정책 null 매칭 버그 수정
[Chore] application.yml gitignore 처리

- 작업 전 이슈 먼저 생성
- 담당자(Assignee) 지정
- 이슈는 PR 하나로 끝낼 수 있는 크기로 쪼개기

---

## 브랜치 전략

| 브랜치 | 설명 |
|---|---|
| `main` | 배포 가능한 안정 버전 (PR로만 머지) |
| `dev` | 개발 통합 브랜치 |
| `feature/*` | 기능 개발 |
| `fix/*` | 버그 수정 |
| `chore/*` | 설정, 환경 등 기타 작업 |

브랜치 이름은 `타입/이슈번호-작업내용` 형식으로, 소문자와 하이픈(-) 사용
feature/1-kakao-login
fix/2-policy-null-matching
chore/3-gitignore-update

---

## 커밋 메시지
type: 제목 (#이슈번호)

| type | 설명 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅 (로직 변경 없음) |
| `refactor` | 리팩토링 |
| `test` | 테스트 코드 |
| `chore` | 빌드, 설정 등 기타 작업 |
feat: 카카오 로그인 구현 (#1)
fix: 정책 null 매칭 버그 수정 (#2)
chore: application.yml gitignore 처리 (#3)

- 제목은 50자 이내, 한국어 사용
- 제목 끝 마침표 생략

---

## Pull Request

제목은 커밋 메시지 형식과 동일하게 작성
변경 내용
상세 작업 내용
테스트 방법
고려 사항

- `dev ← feature` 방향으로 PR
- Squash and merge 통일
- 머지 후 브랜치 삭제

---

## 코드 스타일 💡

**Java / Spring Boot**
- 클래스 `PascalCase` / 메서드·변수 `camelCase` / 상수 `UPPER_SNAKE_CASE`
- 패키지는 도메인 단위로 분리 (`controller / service / repository / dto / entity`)
- DTO와 Entity 분리, Entity setter 지양
- 예외 메시지 한국어 통일
- 응답 형식 `ApiResponse<T>` 통일

---

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

- **수동 트리거 API**: `X-Admin-Key` 헤더에 `ADMIN_API_KEY` 값을 담아 `POST /api/admin/policy/sync` 호출 (온통청년 + KINFA 순차 동기화, 결과를 바로 응답으로 확인 가능). 사용자 JWT로는 호출할 수 없습니다
- **스케줄러 활성화**: `application.yml`의 `policy.sync.scheduler.enabled: true`로 바꾸면 매일 새벽 3시(Asia/Seoul)에 자동 동기화

동기화는 소스별 전체 목록을 받아 정책 테이블을 그 목록에 맞춥니다(신규 추가 / 기존 갱신 / 목록에서 빠진 정책 삭제, 단 사용자가 신청한 정책은 유지).
- **온통청년**: 전체 페이지를 순회해 주거·금융 분류 중 신청이 끝나지 않은 정책만 저장 (마감 코드 `0057003`, 신청기간 종료일이 지난 정책 제외)
- **KINFA**: 응답이 기준연월(`basYm`)별 스냅샷이라 가장 최근 기준연월만 받아 운영 중(`prdExisYn=Y`)인 상품만 저장. 상품 식별자는 `제공기관명|상품명`

또한 스케줄러가 활성화된 환경(배포 기본값)에서는 앱 기동 시 정책 테이블이 비어 있으면 즉시 1회 동기화합니다(`PolicyInitialSyncRunner`). 새로 배포하거나 DB를 초기화한 직후 새벽 3시까지 정책이 비어 있는 문제를 막기 위함입니다.

두 방법 모두 `YOUTH_CENTER_API_KEY`, `KINFA_API_KEY` 등 위 환경변수가 설정되어 있어야 동작합니다.

## 테스트와 CI

- `./gradlew test`는 `src/test/resources/application.yml`(H2 메모리 DB, 테스트용 값)을 사용하므로 `.env`나 로컬 DB 없이 실행됩니다.
- GitHub Actions(`.github/workflows/ci.yml`)가 dev·main 대상 PR과 dev 푸시마다 메인 서버와 mock-bank 테스트를 실행합니다. 실패하면 Actions 화면에서 테스트 리포트를 내려받을 수 있습니다.

## 배포

운영 서버(`~/FinTarget-BE`, dev 브랜치)는 Docker Compose로 `app`(Spring Boot), `db`(PostgreSQL), `redis`, 시연용 `mock-bank`(가상 금융 서버, 8081 포트)를 실행합니다.
서버 설정 파일(`.env`, `src/main/resources/application.yml`)은 git에 올리지 않고 서버에만 있습니다.

- **수동 배포**: 서버에서 `~/FinTarget-BE/scripts/deploy.sh`
  - dev를 fast-forward로 받고, 메인 서버 코드가 바뀐 경우에만 `app` 컨테이너를 다시 빌드합니다(DB·Redis는 재시작하지 않음).
  - `/v3/api-docs`가 180초 안에 200을 응답하지 않으면 이전 커밋으로 되돌려 다시 빌드합니다.
  - mock-bank 코드가 바뀌었거나 mock-bank 컨테이너가 떠 있지 않으면 `mock-bank`만 다시 빌드합니다.
    시연용 보조 서버라 실패해도 메인 서버는 되돌리지 않고 종료 코드 1로 알립니다. `.env`에 `MOCK_BANK_USERNAME`·`MOCK_BANK_PASSWORD`가 없으면 건너뜁니다.
  - 배포가 끝나면 오래된 이미지·빌드 캐시를 정리합니다(서버 디스크 19GB).
- **자동 배포(CD)**: `.github/workflows/cd.yml`이 dev 푸시의 CI가 성공하면 SSH로 위 스크립트를 실행합니다.
  저장소 Secrets(`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`)를 등록하기 전까지는 배포 단계를 건너뜁니다.
  - `DEPLOY_SSH_KEY`는 배포 전용으로 새로 만든 키를 서버 `~/.ssh/authorized_keys`에 추가해 사용합니다(개인 키 재사용 금지).
  - `DEPLOY_KNOWN_HOSTS`는 `ssh-keyscan <서버 주소>` 결과입니다.
- 컨테이너는 root가 아닌 전용 계정으로, 한국 시간대(`-Duser.timezone=Asia/Seoul`)로 실행됩니다.
