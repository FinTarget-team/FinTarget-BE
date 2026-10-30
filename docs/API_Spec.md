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

### 목표 조회
- **GET** `/api/goals`
- Response: 목표 없으면 `data: null`, 있으면 아래 형식
```json
{
  "goalId": "uuid",
  "title": "string",
  "targetAmount": 100000000,
  "currentAmount": 5000000,
  "deadline": "2027-12-31",
  "deadlineDefaulted": false,
  "status": "ACTIVE",
  "createdAt": "datetime",
  "updatedAt": "datetime",
  "progressRate": 5.0,
  "dday": 567
}
```
> `status` 값: `ACTIVE` | `COMPLETED` | `ABANDONED`
> `dday`: 양수=남은 일수, 0=오늘, 음수=기한 초과
> `deadlineDefaulted`: 온보딩 자동 생성 시 채운 기본 마감일(생성일 + 1년)이면 `true`. 목표 수정에서 마감일을 바꾸면 `false`. `true`일 때는 시뮬레이션 성공 확률을 노출하지 않음
> `targetAmount`: 1원 이상 50억 원 이하 (생성/수정 공통)

### 목표 생성
- **POST** `/api/goals`
- Request Body:
```json
{
  "title": "전세 자금 마련",
  "targetAmount": 100000000,
  "currentAmount": 5000000,
  "deadline": "2027-12-31"
}
```

### 목표 수정
- **PUT** `/api/goals`
- Request Body: 목표 생성과 동일

### 목표 삭제
- **DELETE** `/api/goals`

---

## 5. 정책 (Policy)

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
- Response: 배열
```json
[
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
```
- `regions`: 신청 가능 지역 코드 목록 (사용자 `region`과 같은 17개 시/도 코드). 빈 배열이면 전국 정책. `/api/policies/my` 응답의 `policy`에도 같은 필드가 포함됨

### 내 정책 목록 조회
- **GET** `/api/policies/my`
- Response: 배열
```json
[
  {
    "userPolicyId": "uuid",
    "policy": { },
    "status": "APPLIED"
  }
]
```
> `status` 값: `APPLIED` | `INTEREST` | `ABANDONED`

### 내 정책 추가
- **POST** `/api/policies/my`
- Request Body:
```json
{
  "policyId": "uuid",
  "status": "APPLIED"
}
```

### 내 정책 삭제
- **DELETE** `/api/policies/my/{userPolicyId}`
- Path Parameter: `userPolicyId` (uuid)

---

## 6. 시뮬레이션 (Simulation)

### 시뮬레이션 목록 조회
- **GET** `/api/simulations`
- Response: 배열
```json
[
  {
    "simulationId": "uuid",
    "goalId": "uuid",
    "monthlySaving": 500000,
    "expectedCompletionDate": "2027-12-31",
    "policyCompletionDate": "2027-06-30",
    "createdAt": "datetime"
  }
]
```

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

### 목표별 시뮬레이션 조회
- **GET** `/api/simulations/goal/{goalId}`
- Path Parameter: `goalId` (uuid)

---

## 7. 지출 (Expense)

### 지출 전체 조회
- **GET** `/api/expenses`
- Response: 배열
```json
[
  {
    "expenseId": "uuid",
    "amount": 50000,
    "category": "FOOD",
    "description": "string",
    "spentAt": "2026-06-12",
    "createdAt": "datetime"
  }
]
```

### 기간별 지출 조회
- **GET** `/api/expenses/period`
- Query Parameters:
    - `start` (필수): 시작일 (yyyy-MM-dd)
    - `end` (필수): 종료일 (yyyy-MM-dd)

### 지출 동기화
- **POST** `/api/expenses/sync`
- Request Body: 배열
```json
[
  {
    "amount": 50000,
    "category": "FOOD",
    "description": "string",
    "spentAt": "2026-06-12"
  }
]
```

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