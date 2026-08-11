# Fin-Target Convention

## 작업 순서 ✔️

1. 이슈 생성
2. 이슈 기반 브랜치 생성
3. 작업 및 커밋
4. dev 브랜치로 PR 생성
5. 리뷰 후 Squash and merge
6. 브랜치 삭제

---

## 이슈 🗂️

제목은 `[타입] 작업 내용` 형식으로 작성
[Feat] 카카오 로그인 구현
[Fix] 정책 null 매칭 버그 수정
[Chore] application.yml gitignore 처리

- 작업 전 이슈 먼저 생성
- 담당자(Assignee) 지정
- 이슈는 PR 하나로 끝낼 수 있는 크기로 쪼개기

---

## 브랜치 전략 🌱

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

## 커밋 메시지 📝
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

## Pull Request 🔁

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

## 환경변수 설정 🔑

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