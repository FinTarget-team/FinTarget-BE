# FinTarget API 명세서 (iOS 프론트 전달용)

> 작성일: 2026-06-12
> 베이스 URL: `http://localhost:8080` (개발 서버)
> 인증: 모든 API는 JWT Bearer 토큰 필요 (로그인 API 제외)

---

## 인증 방법

모든 요청 헤더에 아래 형식으로 토큰 포함:
Authorization: Bearer {accessToken}

---

## 1. 인증 (Auth)

### 카카오 로그인
- **POST** `/auth/kakao/login`
- 인증 불필요
- Request Body:
```json
{
  "authorizationCode": "string",
  "redirectUri": "string"
}
```
- Response:
```json
{
  "status": 201,
  "message": "Created",
  "data": {
    "userId": "string",
    "accessToken": "string",
    "refreshToken": "string",
    "expiresIn": 3600,
    "provider": "KAKAO",
    "newUser": true
  }
}
```

### 네이버 로그인
- **POST** `/auth/naver/login`
- 인증 불필요
- Request Body:
```json
{
  "authorizationCode": "string",
  "state": "string"
}
```

### 토큰 갱신
- **POST** `/auth/token/refresh`
- Request Body:
```json
{
  "refreshToken": "string"
}
```

### 로그아웃
- **POST** `/auth/logout`

---

## 2. 유저 (User)

### 내 프로필 조회
- **GET** `/users/me`
- Response:
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "userId": "string",
    "name": "string",
    "email": "string",
    "region": "string",
    "provider": "string"
  }
}
```

### 프로필 수정
- **PUT** `/users/me/profile`
- Request Body:
```json
{
  "name": "string",
  "region": "string"
}
```

---

## 3. 온보딩 (Onboarding)

### 온보딩 단계 조회
- **GET** `/onboarding/step`
- Query Parameters:
    - `step` (optional, default: 1): 조회할 단계 번호
- Response (1단계, 이름 입력):
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "step": 1,
    "totalSteps": 7,
    "progress": 0.14,
    "question": "사용할 닉네임을 입력해 주세요. (최대 20자)",
    "answerType": "TEXT",
    "options": [],
    "complete": false
  }
}
```
- Response (2단계, 출생연도 입력):
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "step": 2,
    "totalSteps": 7,
    "progress": 0.29,
    "question": "태어난 연도를 입력해 주세요. (예: 2000)",
    "answerType": "NUMBER",
    "options": [],
    "complete": false
  }
}
```
- Response (3단계 예시, CHIP 타입):
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "step": 3,
    "totalSteps": 7,
    "progress": 0.43,
    "question": "string",
    "answerType": "CHIP",
    "options": [
      { "label": "직장인", "value": "EMPLOYED" }
    ],
    "complete": false
  }
}
```

### 온보딩 답변 제출
- **POST** `/onboarding/answer`
- Request Body:
```json
{
  "step": 1,
  "value": "홍길동"
}
```
- 1단계 `value`는 이름(닉네임)이며, 앞뒤 공백은 제거되어 저장되고 공백만 있거나 빈 문자열이거나 20자를 초과하면 400으로 거부됨
- 2단계 `value`는 출생연도(ASCII 숫자 4자리 문자열, 1940년 이상 (올해-14)년 이하, 올해는 Asia/Seoul 기준)이며, 앞뒤 공백은 제거되고 형식이나 범위가 맞지 않으면 400으로 거부됨
- 6단계 `value`는 주택 보유 여부 코드(`HOMELESS` 무주택, `HOMEOWNER` 유주택, `NOT_SPECIFIED` 선택 안 함)이며, 그 외 값은 400으로 거부됨. `NOT_SPECIFIED`는 사용자 정보에 값 없음으로 저장되고 프로필 조회 시 `NOT_SPECIFIED`로 응답됨
- 7단계 `value`는 목표 금액(원, 숫자 문자열, 1원 이상 50억 원 이하)
- 7단계(마지막) 제출 시 온보딩이 완료되고 목표(Goal)가 자동 생성됨: `title` "내 목표", `currentAmount` 0, `deadline` 1년 후, `deadlineDefaulted` true
- 7단계를 다시 제출하면 새로 만들지 않고 기존 목표의 `targetAmount`만 갱신

---

## 4. 목표 (Goal)

> 사용자당 목표는 1개입니다. 그래서 목표 API는 경로에 ID 없이 로그인 사용자의 목표를 대상으로 동작합니다.
> 이 섹션의 모든 응답은 공통 형식 `{ "status", "message", "data" }`로 감싸서 내려옵니다.

### 목표 조회
- **GET** `/api/goals`
- Response (200): 목표가 없으면 `data: null`(에러 아님), 있으면 아래 형식
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "goalId": "uuid",
    "title": "전세 자금 마련",
    "targetAmount": 100000000,
    "currentAmount": 5000000,
    "deadline": "2027-12-31",
    "deadlineDefaulted": false,
    "status": "ACTIVE",
    "createdAt": "2026-10-01T09:00:00",
    "updatedAt": "2026-10-15T15:30:00",
    "progressRate": 5.0,
    "dday": 427
  }
}
```

| 필드 | 설명 |
|---|---|
| `status` | `ACTIVE` \| `COMPLETED` \| `ABANDONED` |
| `progressRate` | 달성률(%) = `currentAmount / targetAmount * 100`. 초과 달성 시 100보다 클 수 있음 |
| `dday` | 마감일까지 남은 일수. 양수=남음, 0=오늘, 음수=기한 초과 |
| `deadlineDefaulted` | 온보딩 자동 생성 시 채운 기본 마감일(생성일 + 1년)이면 `true`. 목표 수정을 한 번이라도 하면 `false`. `true`일 때는 시뮬레이션 성공 확률을 노출하지 않는 것을 권장 |

### 목표 생성
- **POST** `/api/goals`
- 온보딩을 마치면 목표가 자동으로 생성되므로(3. 온보딩 참고), 일반적인 흐름에서는 생성 대신 **목표 수정**을 사용합니다.
- Request Body:
```json
{
  "title": "전세 자금 마련",
  "targetAmount": 100000000,
  "currentAmount": 5000000,
  "deadline": "2027-12-31"
}
```
- Response (201): `"status": 201`, `"message": "Created"`, `data`는 목표 조회와 같은 형식

### 목표 수정
- **PUT** `/api/goals`
- Request Body: 목표 생성과 동일 (네 필드를 모두 보내야 하며, 부분 수정은 지원하지 않음)
- Response (200): `data`는 수정된 목표 (목표 조회와 같은 형식)

### 목표 삭제
- **DELETE** `/api/goals`
- 목표에 연결된 **시뮬레이션 기록도 함께 삭제**됩니다.
- Response (200): `data: null`

### 요청 필드 규칙 (생성·수정 공통)

| 필드 | 필수 | 규칙 |
|---|---|---|
| `title` | O | 문자열 |
| `targetAmount` | O | 1원 이상 50억 원 이하 |
| `currentAmount` | O | 현재까지 모은 금액(원) |
| `deadline` | O | `yyyy-MM-dd` |

> `title`, `currentAmount`, `deadline`은 현재 서버에서 누락 검증을 하지 않아, 빠지면 400이 아니라 500이 발생할 수 있습니다. 반드시 모두 보내 주세요.

### 에러 응답

| 상황 | 상태 | 메시지 |
|---|---|---|
| `targetAmount` 누락, 0 이하 | 400 | `목표 금액은 0보다 커야 합니다` |
| `targetAmount` 50억 원 초과 | 400 | `목표 금액은 50억 원 이하로 입력해주세요` |
| 목표가 이미 있는데 생성 | 409 | `이미 목표가 존재합니다.` |
| 목표가 없는데 수정·삭제 | 400 | `목표가 존재하지 않습니다.` |

---

## 5. 정책 (Policy)

> 정책 데이터는 온통청년(청년 정책)과 서민금융진흥원(KINFA, 서민 금융상품) 공공 API에서 가져와 **매일 03:00(KST)**에 동기화합니다.
> 이 섹션의 모든 응답은 공통 형식 `{ "status", "message", "data" }`로 감싸서 내려옵니다.

### 정책 목록 조회 (매칭)
- **GET** `/api/policies`
- 로그인 사용자(토큰)의 온보딩 정보로 조건에 맞는 정책을 반환함. 페이지네이션 없이 전체 목록을 반환
- Query Parameters:
    - `policyType` (optional): 정책 유형 (`HOUSING` | `FINANCE_WELFARE` | `STARTUP` | `ETC`, 대소문자 구분 없음). 그 외 값은 400
- 매칭 조건
    - 나이: 출생연도가 있으면 만 나이 후보 [올해-출생연도-1, 올해-출생연도] 중 하나라도 정책 `minAge`~`maxAge`에 들면 포함 (null은 제한 없음). 출생연도 입력 전 사용자는 기존 연령대 값으로 비교
    - 소득: 사용자 소득 구간의 대표값이 정책 `incomeLimit` 이하이면 포함 (null은 제한 없음)
    - 지역: `regions`가 빈 배열(전국)이거나 사용자 거주 지역 코드가 `regions`에 포함되면 포함. 거주 지역 미입력 사용자는 전국 정책만
    - 나이 또는 소득을 입력하지 않은 사용자는 빈 배열 (에러 아님)
- Response (200):
```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "policyId": "uuid",
      "name": "청년 월세 지원",
      "description": "string",
      "minAge": 19,
      "maxAge": 34,
      "incomeLimit": null,
      "benefitAmount": null,
      "policyType": "HOUSING",
      "regions": ["GYEONGNAM", "ULSAN"]
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `minAge`, `maxAge` | 지원 대상 나이. `null`이면 해당 방향 제한 없음 |
| `incomeLimit` | 소득 상한(원). `null`이면 소득 제한 없음 |
| `benefitAmount` | 지원 금액(원). 현물·서비스처럼 금액으로 환산되지 않는 혜택이면 `null` |
| `policyType` | `HOUSING`(주거) \| `FINANCE_WELFARE`(금융·복지) \| `STARTUP`(창업) \| `ETC`(기타) |
| `regions` | 신청 가능 지역 코드 목록 (사용자 `region`과 같은 17개 시/도 코드). 빈 배열이면 전국 정책 |

- 에러: `policyType`이 허용값이 아니면 400 `유효하지 않은 정책 유형입니다: {입력값}`

### 내 정책 목록 조회
- **GET** `/api/policies/my`
- 사용자가 관심·신청·포기로 등록한 정책 목록. 없으면 빈 배열
- Response (200):
```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "userPolicyId": "uuid",
      "policy": {
        "policyId": "uuid",
        "name": "청년 월세 지원",
        "description": "string",
        "minAge": 19,
        "maxAge": 34,
        "incomeLimit": null,
        "benefitAmount": null,
        "policyType": "HOUSING",
        "regions": []
      },
      "status": "INTEREST"
    }
  ]
}
```
> `policy`는 정책 목록 조회의 항목과 같은 형식입니다.
> `status` 값: `APPLIED`(신청) | `INTEREST`(관심) | `ABANDONED`(포기)

### 내 정책 추가
- **POST** `/api/policies/my`
- Request Body:
```json
{
  "policyId": "uuid",
  "status": "INTEREST"
}
```
- Response (201): `"status": 201`, `"message": "Created"`, `data`는 내 정책 목록의 항목 하나
- `userPolicyId`는 시뮬레이션 실행 시 정책 혜택을 반영할 때 사용합니다(6. 시뮬레이션 참고).

| 상황 | 상태 | 메시지 |
|---|---|---|
| 존재하지 않는 `policyId` | 400 | `존재하지 않는 정책입니다.` |
| 이미 등록한 정책 | 409 | `이미 등록된 정책입니다.` |

> `status`는 반드시 `APPLIED` \| `INTEREST` \| `ABANDONED` 중 하나를 대문자로 보내 주세요. 그 외 값이나 누락은 현재 400(서버 내부 메시지) 또는 500으로 응답될 수 있습니다.

### 내 정책 삭제
- **DELETE** `/api/policies/my/{userPolicyId}`
- Path Parameter: `userPolicyId` (uuid)
- Response (200): `data: null`

| 상황 | 상태 | 메시지 |
|---|---|---|
| 존재하지 않는 `userPolicyId` | 400 | `존재하지 않는 정책입니다.` |
| 다른 사용자의 정책 | 409 | `본인의 정책만 삭제할 수 있습니다.` |

---

## 6. 시뮬레이션 (Simulation)

> 목표의 남은 금액(`targetAmount - currentAmount`)을 월 저축액과 연이율(월 복리)로 모으는 데 걸리는 기간을 계산합니다. 매달 저축액이 평균 대비 약 ±20% 변동한다고 가정해 **1만 회 몬테카를로 시뮬레이션**을 돌리고, 결과를 저장합니다.
> 이 섹션의 모든 응답은 공통 형식 `{ "status", "message", "data" }`로 감싸서 내려옵니다.

### 시뮬레이션 실행
- **POST** `/api/simulations`
- Request Body:
```json
{
  "goalId": "uuid",
  "monthlySaving": 500000,
  "userPolicyId": "uuid (optional)",
  "annualInterestRate": 0.03
}
```
- Response (200):
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "simulationId": "uuid",
    "goalId": "uuid",
    "monthlySaving": 500000,
    "expectedCompletionDate": "2028-03-15",
    "policyCompletionDate": "2027-11-20",
    "optimisticCompletionDate": "2027-09-10",
    "pessimisticCompletionDate": "2028-08-01",
    "successProbability": 0.73,
    "createdAt": "2026-10-30T10:00:00"
  }
}
```

| 필드 | 설명 |
|---|---|
| `expectedCompletionDate` | 예상 달성일 (중앙값, 50번째 백분위) |
| `optimisticCompletionDate` | 낙관적 시나리오 달성일 (10번째 백분위, 가장 빠른 쪽) |
| `pessimisticCompletionDate` | 비관적 시나리오 달성일 (90번째 백분위, 가장 늦은 쪽) |
| `policyCompletionDate` | 정책 혜택을 반영한 예상 달성일(중앙값). 정책의 `benefitAmount`(연간)를 12로 나눠 월 저축액에 더해 계산. 요청에 `userPolicyId`를 보내지 않았으면 `null` |
| `successProbability` | 목표 마감일까지 목표 금액을 달성할 확률 (0.0~1.0) |

> 목표의 `deadlineDefaulted`가 `true`면 마감일이 사용자가 정한 날짜가 아니므로, `successProbability`는 화면에 노출하지 않는 것을 권장합니다.
> 낙관·비관 달성일과 성공 확률이 추가되기 전에 저장된 시뮬레이션 기록은 세 값이 `null`일 수 있습니다.
> 현재 `benefitAmount`가 `null`인 정책(금액으로 환산되지 않는 혜택)을 `userPolicyId`로 보내면 서버 오류(500)가 발생합니다. 수정 전까지는 `benefitAmount`가 있는 정책만 보내 주세요.

- 요청 필드 검증 규칙:

| 필드 | 필수 | 규칙 | 실패 시 메시지 |
|---|---|---|---|
| `goalId` | O | 본인 소유 목표의 ID | 누락: `목표를 선택해주세요.` / 없거나 타인 목표: `존재하지 않는 목표입니다.` |
| `monthlySaving` | O | 0보다 큰 정수(원) | 누락: `월 저축액을 입력해주세요.` / 0·음수: `월 저축액은 0보다 커야 합니다.` |
| `userPolicyId` | X | 본인이 스크랩한 정책 ID. 생략하면 `policyCompletionDate`는 `null` | `존재하지 않는 유저 정책입니다.` |
| `annualInterestRate` | X | 0.0 이상 1.0 이하(예: 3% = `0.03`). 생략하면 `0.03` 적용 | 음수: `연이율은 0 이상이어야 합니다.` / 1.0 초과: `연이율은 100%(1.0) 이하여야 합니다.` |

- 검증 실패는 모두 **HTTP 400**으로 응답합니다. 여러 필드가 동시에 틀려도 메시지는 하나만 반환되며, 어떤 필드인지 알려주는 `field` 키는 없습니다.
```json
// monthlySaving: null
{
  "status": 400,
  "message": "월 저축액을 입력해주세요.",
  "data": null
}
```
```json
// monthlySaving: 0 또는 음수
{
  "status": 400,
  "message": "월 저축액은 0보다 커야 합니다.",
  "data": null
}
```

### 시뮬레이션 목록 조회
- **GET** `/api/simulations`
- 로그인 사용자의 모든 시뮬레이션 기록. 없으면 빈 배열. 정렬 순서는 보장하지 않으므로 필요하면 `createdAt`으로 정렬해 주세요.
- Response (200): `data`는 시뮬레이션 실행 응답의 `data`와 같은 형식의 배열

### 목표별 시뮬레이션 조회
- **GET** `/api/simulations/goal/{goalId}`
- Path Parameter: `goalId` (uuid)
- 해당 목표의 본인 시뮬레이션 기록. 없거나 타인 목표면 빈 배열 (에러 아님)
- Response (200): 시뮬레이션 목록 조회와 같은 형식

---

## 7. 지출 (Expense)

> 지출 내역은 클라이언트(앱)가 수집해 서버로 동기화하고, 서버는 조회·통계와 넛지(과소비 알림) 계산에 사용합니다.
> 이 섹션의 모든 응답은 공통 형식 `{ "status", "message", "data" }`로 감싸서 내려옵니다.

### 지출 동기화
- **POST** `/api/expenses/sync`
- Request Body: 배열 (빈 배열이면 아무것도 하지 않음)
```json
[
  {
    "amount": 50000,
    "category": "FOOD",
    "description": "점심",
    "spentAt": "2026-10-12"
  }
]
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `amount` | O | 지출 금액(원) |
| `category` | O | 카테고리 문자열. 서버는 값 목록을 제한하지 않고 받은 그대로 저장·집계함 |
| `description` | X | 메모 |
| `spentAt` | O | 지출일 `yyyy-MM-dd` |

- **중복 방지**: `category`, `amount`, `spentAt`, `description` 네 값이 모두 같은 지출은 이미 저장돼 있으면 건너뜁니다. 같은 목록을 여러 번 보내도 중복 저장되지 않으므로 재시도해도 안전합니다.
- Response (200): `data: null`

> 필수 필드가 빠지면 현재 400이 아니라 500이 발생할 수 있습니다. 필수 필드는 반드시 보내 주세요.

### 지출 전체 조회
- **GET** `/api/expenses`
- 없으면 빈 배열. 정렬 순서는 보장하지 않으므로 필요하면 `spentAt`으로 정렬해 주세요.
- Response (200):
```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "expenseId": "uuid",
      "amount": 50000,
      "category": "FOOD",
      "description": "점심",
      "spentAt": "2026-10-12",
      "createdAt": "2026-10-12T21:00:00"
    }
  ]
}
```

### 기간별 지출 조회
- **GET** `/api/expenses/period`
- Query Parameters:
    - `start` (필수): 시작일 (`yyyy-MM-dd`)
    - `end` (필수): 종료일 (`yyyy-MM-dd`)
- `start`와 `end` 당일을 **모두 포함**합니다.
- Response (200): 지출 전체 조회와 같은 형식

### 카테고리별 지출 통계
- **GET** `/api/expenses/stats`
- Query Parameters:
    - `start` (필수): 시작일 (`yyyy-MM-dd`)
    - `end` (필수): 종료일 (`yyyy-MM-dd`)
- `start`와 `end` 당일을 **모두 포함**합니다.
- Response (200):
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "totalAmount": 320000,
    "byCategory": [
      { "category": "FOOD", "amount": 200000 },
      { "category": "TRANSPORT", "amount": 120000 }
    ]
  }
}
```
- `byCategory`: 기간 내 지출이 없는 카테고리는 포함되지 않습니다. 정렬 순서는 보장하지 않습니다. 기간 내 지출이 없으면 `totalAmount: 0`, `byCategory: []`
- 에러: `start`가 `end`보다 늦으면 400 `시작일이 종료일보다 늦을 수 없습니다.`

> 기간별 조회와 통계에서 `start`·`end`가 빠지거나 날짜 형식이 틀리면, 현재 400이 아니라 500이 발생할 수 있습니다.

---

## 8. 넛지 (Nudge)

### 넛지 목록 조회
- **GET** `/nudges`
- Response:
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "unreadCount": 0,
    "nudges": []
  }
}
```

### 넛지 읽음 처리
- **PATCH** `/nudges/{nudgeId}/read`
- Path Parameter: `nudgeId` (string)

---

## 공통 에러 응답

| 상태코드 | 설명 |
|---------|------|
| 400 | 잘못된 요청 (파라미터 누락 등) |
| 401 | 인증 실패 (토큰 없음/만료) |
| 500 | 서버 오류 |

---

## 참고 사항

- 스웨거 UI: `http://localhost:8080/swagger-ui.html`
- 스웨거 JSON: `http://localhost:8080/v3/api-docs`
- JWT 토큰 만료 시간: 1시간 (3600초)
- 카카오 로그인 시 매번 신규 유저 생성되는 버그 수정 예정 (이슈 #10)