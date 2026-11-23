# FinTarget (핀타겟)

> **청년의 목표 자금 마련을 돕는 금융 목표 관리 앱**
> 목표까지 가장 빠르게, 똑똑하게

홍익대학교 2026 졸업 프로젝트 · 이 저장소는 FinTarget의 **백엔드 API 서버**와 시연용 **가상 금융 서버(mock-bank)** 를 담고 있습니다.

| 저장소 | 설명 |
|---|---|
| [FinTarget-BE](https://github.com/FinTarget-team/FinTarget-BE) | 백엔드 API 서버 (Spring Boot) + 가상 금융 서버(`mock-bank/`) |
| [FinTarget-iOS](https://github.com/FinTarget-team/FinTarget-iOS) | iOS 앱 (Swift) |

---

## 왜 만들었나요?

전세 보증금, 내 집 마련, 결혼 자금처럼 청년이 모아야 하는 목돈은 크고 기간은 깁니다. 그런데

- **"지금처럼 모으면 언제 모을 수 있을까?"** 를 알기 어렵고,
- 청년도약계좌·월세 지원·청년 전세대출 같은 **정책이 많지만 나에게 맞는 걸 찾기 어렵고**,
- 목표를 세워도 **이번 달 소비가 목표에 어떤 영향을 주는지** 체감하기 어렵습니다.

FinTarget은 이 세 가지를 **목표 → 시뮬레이션 → 정책 → 소비 알림** 흐름으로 연결합니다.

---

## 주요 기능

### 1. 목표 설정
- 목표 이름, 목표 금액, 현재까지 모은 금액, 마감일을 정해 **진행 중인 목표 1개**를 관리합니다.
- 목표 금액이나 마감일을 다시 정하면 새 목표로 바뀌고, **이전 목표와 그때의 시뮬레이션 기록은 그대로 보존**됩니다.

### 2. 목표 달성 시뮬레이션 (몬테카를로)
- 월 저축액을 입력하면 **1만 번의 몬테카를로 시뮬레이션**으로 목표 달성 시점을 계산합니다.
  - 매달 저축액이 평균 ±20% 범위에서 흔들린다고 가정하고, 연 이자율(기본 3%)을 반영합니다.
- 결과
  - **예상 달성일**: 중앙값(50%) 기준, 낙관적(10%)·비관적(90%) 시나리오와 함께 제공
  - **달성 확률**: 마감일까지 목표 금액을 모을 확률
  - **정책 반영 결과**: 받을 수 있는 정책 혜택(월 지원금, 1회 지급 등)을 더했을 때의 예상 달성일과 확률
- 실행할 때마다 기록이 남아, 목표별로 시뮬레이션 이력을 비교할 수 있습니다.

### 3. 맞춤 청년 정책 추천
- **온보딩**에서 나이, 소득, 거주 지역, 고용 형태, 주거 형태를 입력받아 조건에 맞는 정책만 보여줍니다.
- 정책 데이터는 공공 API에서 매일 새벽 3시에 자동으로 동기화합니다.
  - **온통청년**: 주거·금융 분야 청년 정책 (신청 마감된 정책 제외)
  - **서민금융진흥원(금융위원회)**: 운영 중인 서민금융 상품
- 관심 있는 정책은 **스크랩**해 따로 모아볼 수 있습니다.

### 4. AI 정책 검색 · 질문 (RAG)
- **자연어 검색**: "월세 부담을 줄이고 싶어요"처럼 질문하면, 내 조건에 맞는 정책 중 **의미가 가까운 정책**을 찾아줍니다.
- **통합 AI 답변**: 여러 정책을 근거로 답변을 만들고, 어떤 정책의 어느 부분을 근거로 했는지 **출처를 함께** 보여줍니다.
- **정책별 AI 질문**: 정책 상세 화면에서 "제가 신청할 수 있나요?" 같은 질문을 하면, 정책 원문과 내 정보를 바탕으로 대화형으로 답합니다.
- 정책 원문을 조각(청크)으로 나눠 임베딩하고 **pgvector**로 유사도 검색합니다. 사용자별 하루 사용 한도가 있습니다.

### 5. 지출 관리
- 지출을 6개 카테고리(식비, 교통, 쇼핑, 문화/여가, 생활, 기타)로 기록하고, **기간별 내역과 카테고리 통계**를 제공합니다.
- 앱에서 직접 입력하거나, **은행 거래 웹훅**으로 자동 수집합니다. 같은 지출이 두 번 들어와도 한 번만 저장됩니다.
- 새 지출이 저장되면 **실시간 이벤트(SSE)** 로 앱에 바로 알려, 화면을 새로고침하지 않아도 반영됩니다.

### 6. 넛지 (행동 유도 알림)
- **이상 지출 알림**: 이번 달 카테고리 지출이 평소 월평균보다 50% 이상 늘면 알려줍니다.
- **목표 마감 임박 알림**: 마감일이 30일 이내로 다가오면 알려줍니다.
- 읽음 처리, 삭제, 안 읽은 개수 표시를 지원합니다.

### 7. 가상 금융 서버 (mock-bank)
- 실제 은행 연동 대신, 시연을 위해 **은행처럼 결제를 발생시켜 핀타겟으로 보내는** 별도 서버입니다.
- 버튼 몇 번으로 지난 3개월 소비 기준선 → 이번 달 랜덤 소비 → 식비 급증 데이터를 만들어 **이상 지출 넛지까지 이어지는 흐름**을 시연할 수 있습니다.
- 자세한 사용법은 [`mock-bank/README.md`](mock-bank/README.md)를 참고하세요.

## 시스템 구성

```
iOS 앱 (Swift) ──── HTTP/JSON + JWT ────▶ FinTarget API 서버 (:8080, Spring Boot)
               ◀─── SSE 실시간 이벤트 ───        │
                                                 ├─ PostgreSQL 16 + pgvector (데이터, 정책 임베딩)
mock-bank (:8081) ── 은행 거래 웹훅 ─────▶      ├─ OpenAI API (임베딩, 답변 생성)
                                                 └─ 온통청년 · 금융위원회 공공 API (정책 동기화)

모두 AWS EC2 한 대에서 Docker Compose로 실행 · GitHub Actions로 테스트와 자동 배포
```

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language / Framework | Java 25, Spring Boot 4.0 (Web MVC, Data JPA, Security, Validation) |
| Database | PostgreSQL 16 + pgvector (운영), H2 (테스트) |
| 인증 | 카카오·네이버 소셜 로그인, JWT (액세스·리프레시 토큰, 로그아웃 토큰 블랙리스트) |
| AI | OpenAI API (답변 생성, `text-embedding-3-small` 임베딩), RAG |
| 외부 데이터 | 온통청년 청년정책 API, 금융위원회 서민금융상품 API |
| 실시간 | Server-Sent Events (SSE) |
| 문서화 | springdoc-openapi (Swagger UI) |
| 인프라 | AWS EC2, Docker / Docker Compose |
| CI/CD | GitHub Actions (PR·dev 푸시 테스트, dev 머지 시 자동 배포) |

## 도메인 구성

`src/main/java/kr/fintarget/api/domain/`

| 패키지 | 역할 |
|---|---|
| `auth` | 소셜 로그인, 토큰 발급·재발급, 로그아웃 |
| `user` | 내 프로필 조회·수정, 회원 탈퇴 |
| `onboarding` | 온보딩 질문 단계, 답변 저장 |
| `goal` | 목표 생성·조회·수정·삭제 |
| `simulation` | 몬테카를로 시뮬레이션, 이력 조회 |
| `policy` | 정책 동기화, 맞춤 추천, 스크랩, 원문 청크·임베딩, 자연어 검색, RAG 답변 |
| `ai` | 정책 AI 대화, OpenAI 클라이언트, 사용자별 일일 사용량 제한 |
| `expense` | 지출 기록·통계, 은행 웹훅 수신 |
| `nudge` | 이상 지출·마감 임박 알림 |
| `realtime` | SSE 실시간 이벤트 |

## API 문서

- Swagger UI: `https://<도메인>/swagger-ui/index.html` (HTTPS 적용 전에는 `http://<서버 주소>:8080/...`). 서버에 `SWAGGER_USERNAME`·`SWAGGER_PASSWORD`가 설정되어 있으면 Basic Auth 로그인이 필요합니다.
- 상세 명세: [`docs/API_Spec.md`](docs/API_Spec.md)
- 모든 응답은 `ApiResponse<T>` 형식으로 통일되어 있습니다.

## 실행 방법

```bash
# 1. 테스트 (.env·DB 없이 실행 가능)
./gradlew test

# 2. 로컬 실행: .env.example을 참고해 환경변수를 설정한 뒤
./gradlew bootRun

# 3. Docker Compose로 전체 실행 (app, db, redis, mock-bank)
cp .env.example .env   # 값 채우기
docker compose up -d --build
```

환경변수 설정, 정책 데이터 채우기, 배포 방법은 아래 각 섹션을 참고하세요.

---

# 개발 컨벤션

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

## 코드 스타일

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
  - `/actuator/health`가 180초 안에 200을 응답하지 않으면 이전 커밋으로 되돌려 다시 빌드합니다.
  - `.env`에 `DOMAIN`이 있으면 HTTPS 프록시(Caddy)를 띄웁니다. `deploy/Caddyfile`이 바뀌었거나 떠 있지 않을 때만 다시 띄우며, 실패해도 앱은 되돌리지 않고 종료 코드 1로 알립니다.
  - mock-bank 코드가 바뀌었거나 mock-bank 컨테이너가 떠 있지 않으면 `mock-bank`만 다시 빌드합니다.
    시연용 보조 서버라 실패해도 메인 서버는 되돌리지 않고 종료 코드 1로 알립니다. `.env`에 `MOCK_BANK_USERNAME`·`MOCK_BANK_PASSWORD`가 없으면 건너뜁니다.
  - 배포가 끝나면 오래된 이미지·빌드 캐시를 정리합니다(서버 디스크 19GB).
- **자동 배포(CD)**: dev에 머지되어 CI가 성공하면 `.github/workflows/cd.yml`이 SSH로 위 스크립트를 실행합니다. Actions → CD → Run workflow로 수동 실행도 할 수 있습니다.
  - 저장소 Secrets: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` (하나라도 없으면 배포 단계를 건너뜀)
  - `DEPLOY_SSH_KEY`는 배포 전용 키입니다. 서버 `authorized_keys`에 `restrict,command="bash ~/FinTarget-BE/scripts/deploy.sh"` 옵션으로 등록되어, 이 키로는 배포 스크립트 외의 명령을 실행할 수 없습니다.
  - `DEPLOY_KNOWN_HOSTS`는 `ssh-keyscan <서버 주소>` 결과입니다. 서버를 새로 만들면 다시 등록해야 합니다.
  - GitHub Actions 실행 서버의 IP가 고정되어 있지 않아 EC2 보안 그룹의 22번 포트는 전체(`0.0.0.0/0`)에 열려 있고, 서버는 비밀번호 로그인을 막고 키 로그인만 허용합니다.
  - **CD가 실패하면** Actions 실행 화면의 Summary와 단계 로그에서 원인을 확인합니다. 이 워크플로는 기본 브랜치(main)에 있는 파일로 실행되므로, `cd.yml` 수정은 main까지 머지해야 적용됩니다.

    | 로그 메시지 | 원인 · 조치 |
    |---|---|
    | `DEPLOY_SSH_KEY를 SSH 개인 키로 읽을 수 없습니다` | 키 값이 잘림·형식 오류. `-----BEGIN ...-----`부터 `-----END ...-----`까지 다시 등록 |
    | `DEPLOY_KNOWN_HOSTS에 DEPLOY_HOST 항목이 없습니다` | `ssh-keyscan <서버 주소>` 결과를 다시 등록 |
    | `Host key verification failed` | 서버를 새로 만들었거나 known_hosts 값이 다름. 위와 같이 다시 등록 |
    | `Permission denied (publickey)` | 서버 `authorized_keys`의 배포 키와 Secret의 키가 다름 |
    | `Connection timed out` | 서버 꺼짐 또는 보안 그룹 22번 포트 차단 |
    | 배포 결과: 실패 (종료 코드 1) | SSH는 성공, 배포 스크립트 실패(앱 헬스 체크 실패 후 롤백, mock-bank 기동 실패 등). Summary의 로그 확인 |
- **HTTPS** (#218): Caddy가 `DOMAIN`으로 Let's Encrypt 인증서를 자동 발급·갱신하고 `app:8080`으로 전달합니다 (`deploy/Caddyfile`).
  1. 도메인의 A 레코드를 서버 공인 IP로 지정합니다. 도메인이 없으면 `<IP를 -로 이은 값>.sslip.io`(예: `1-2-3-4.sslip.io`)를 쓸 수 있습니다.
  2. EC2 보안 그룹에서 80·443(TCP)과 443(UDP)을 엽니다. 80은 인증서 발급·갱신에 필요합니다.
  3. 서버 `.env`에 `DOMAIN=<도메인>`을 넣고 `scripts/deploy.sh`를 실행합니다. `https://<도메인>/actuator/health`가 200이면 완료입니다.
  4. iOS 앱이 `https://<도메인>`으로 바뀌어 배포되면 `.env`에 `APP_BIND_ADDRESS=127.0.0.1`을 넣고 `FORCE_BUILD=1 scripts/deploy.sh`로 다시 띄워 8080을 외부에서 막고, 보안 그룹에서 8080을 닫습니다.
  - 앱은 `server.forward-headers-strategy=native`로 Caddy가 붙인 `X-Forwarded-For`의 실제 클라이언트 IP를 씁니다(Docker 내부망 프록시만 신뢰). `/auth/**` 요청 횟수 제한(IP별 1분 30회)이 이 IP 기준입니다.
- **DB 스키마**: 운영 앱은 `ddl-auto: validate`로 실행되어 엔티티가 바뀌어도 스키마를 자동으로 바꾸지 않습니다(`docker-compose.yml`이 서버 `application.yml`보다 우선).
  - 테이블·컬럼을 추가·변경하는 PR은 `docs/sql/<이슈번호>_*.sql`을 함께 올리고, **배포 전에** 서버에서 직접 실행합니다.
    `docker exec -i fintarget-db psql -U "$DB_USERNAME" -d "$DB_NAME" < docs/sql/<파일>.sql`
  - SQL을 적용하지 않고 배포하면 엔티티와 DB가 달라 앱이 뜨지 않고, `deploy.sh`가 이전 버전으로 되돌립니다.
  - 급하게 띄워야 하면 서버 `.env`에 `JPA_DDL_AUTO=update`를 넣어 예전 방식으로 실행할 수 있습니다. SQL 적용 후 다시 지웁니다.
- **DB 백업**: `scripts/backup-db.sh`가 `pg_dump`로 `~/backups/`에 백업하고 14일보다 오래된 파일을 지웁니다. 서버 cron에 등록해 매일 실행합니다.
  `0 4 * * * ~/FinTarget-BE/scripts/backup-db.sh >> ~/backups/backup.log 2>&1` (복원 방법은 스크립트 주석 참고)
- 컨테이너는 root가 아닌 전용 계정으로, 한국 시간대(`-Duser.timezone=Asia/Seoul`)로 실행됩니다.
