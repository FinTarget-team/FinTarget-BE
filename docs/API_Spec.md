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
- **POST** `/auth/naver/login/token`
- 인증 불필요
- iOS/Android 네이버 SDK가 발급한 액세스 토큰을 보내면 서버가 네이버 사용자 정보 API로 검증한 뒤 로그인/회원가입을 처리합니다.
- 인가 코드 방식(`/auth/naver/login`)은 지원하지 않습니다.
- Request Body:
```json
{
  "accessToken": "string"
}
```
- Response: 카카오 로그인과 동일 (`provider`: `"NAVER"`). 유효하지 않거나 만료된 토큰이면 401

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
    "email": "user@example.com",
    "region": "string",
    "provider": "KAKAO"
  }
}
```
- `email`: 소셜 로그인(카카오·네이버)에서 받은 이메일. 이메일 제공에 동의하지 않았으면 `null`. 가입 후 이메일이 비어 있으면 다음 로그인 때 제공자 이메일로 채워지며, 이미 있는 이메일은 바뀌지 않음
- `provider`: 가입에 사용한 소셜 로그인 제공자. `KAKAO` 또는 `NAVER`

### 프로필 수정
- **PUT** `/users/me/profile`
- Request Body:
```json
{
  "name": "string",
  "region": "string"
}
```
- 이메일은 수정할 수 없습니다. 요청에 `email`을 보내도 무시되며 오류는 나지 않습니다.

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
| `deadlineDefaulted` | 온보딩 자동 생성 시 채운 기본 마감일(생성일 + 1년)이면 `true`. 목표 수정(`PUT /api/goals`)으로 저장하면 **마감일을 바꾸지 않았더라도** 사용자가 확인한 것으로 보고 `false` (#186). 온보딩 7단계 재제출(금액만 갱신)로는 바뀌지 않음. `true`일 때는 시뮬레이션 성공 확률을 노출하지 않는 것을 권장 |

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
| `title` | O | 공백만으로 된 문자열 불가 |
| `targetAmount` | O | 1원 이상 50억 원 이하 |
| `currentAmount` | O | 현재까지 모은 금액(원). 0 이상 |
| `deadline` | O | `yyyy-MM-dd` |

### 에러 응답

| 상황 | 상태 | 메시지 |
|---|---|---|
| `title` 누락, 공백 | 400 | `목표 제목을 입력해주세요.` |
| `targetAmount` 누락, 0 이하 | 400 | `목표 금액은 0보다 커야 합니다` |
| `targetAmount` 50억 원 초과 | 400 | `목표 금액은 50억 원 이하로 입력해주세요` |
| `currentAmount` 누락 | 400 | `현재 모은 금액을 입력해주세요.` |
| `currentAmount` 음수 | 400 | `현재 모은 금액은 0 이상이어야 합니다.` |
| `deadline` 누락 | 400 | `목표 기한을 입력해주세요.` |
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
    - 소득: 사용자 소득 구간의 대표값이 정책 `incomeLimit` 이하이면 포함. `incomeLimit`이 null(정형화된 소득 상한 없음)이면 소득 조건으로 제외하지 않음
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
      "simulationBenefit": {
        "monthlyAmount": 200000,
        "months": 12,
        "monthsSpecified": true,
        "oneTimeAmount": null,
        "maximum": true
      },
      "ageCondition": null,
      "incomeCondition": "중위소득 150% 이하",
      "supportContent": "월 최대 20만원, 최장 12개월 지원",
      "policyType": "HOUSING",
      "regions": ["GYEONGNAM", "ULSAN"]
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `minAge`, `maxAge` | 지원 대상 나이. `null`이면 해당 방향 제한 없음. 단, KINFA 정책은 나이를 숫자로 저장하지 않아 항상 `null`이며, 나이 조건은 `ageCondition` 참고 |
| `ageCondition` | 나이 조건 원문 (현재 KINFA 정책만 값이 있음). 원본이 조건 없음을 명시하면 `연령 무관`. 원문이 없으면 `null`. 표시 순서: `minAge`·`maxAge` → `ageCondition` → 둘 다 없으면 "제한 없음" |
| `incomeLimit` | 소득 상한(원). `null`이면 정형화된 소득 상한이 없다는 뜻이며, 소득 제한이 없다는 뜻은 아님 (현재 모든 정책이 `null`). 소득 조건은 `incomeCondition` 참고 |
| `benefitAmount` | 지원 금액(원). 현물·서비스처럼 금액으로 환산되지 않는 혜택이거나 원문에서 금액을 읽지 못하면 `null` (온통청년 정책은 항상 `null`). **KINFA 정책도 전부 대출 상품이라 항상 `null`** — 대출 한도는 받는 혜택이 아니라 빌리는 한도이므로 저축액으로 환산하지 않는다. 한도는 `supportContent`의 `대출 한도: ...` 줄 참고 |
| `simulationBenefit` | 시뮬레이션 정책 반영 달성일에 쓰이는 현금 혜택 (#162). 정책 원문에서 AI로 추출한 값이며, 반영할 혜택이 없으면 `null` (대출·이자 지원·보증료, 현물·바우처·실비 지원, 금액을 알 수 없는 정책, 아직 추출 전인 정책). `monthlyAmount`(월 금액, 원)·`months`(계산에 쓰는 개월 수)·`monthsSpecified`(원문에 기간이 있는지. `false`면 `months`는 기본 12개월)·`oneTimeAmount`(1회 금액, 원, 첫 달 지급으로 계산)·`maximum`(`true`면 "최대 지원 금액 기준 예상" 안내 필요). 이 값이 있는 정책만 시뮬레이션의 `policyCompletionDate`가 계산됨 |
| `incomeCondition` | 소득 조건 원문. 원본이 조건 없음을 명시하면 `소득 무관`, 온통청년 연소득 조건은 `연소득 N만원 이하` 형식. 원문이 없으면 `null`(정보 없음). 숫자 값(`incomeLimit`)이 없을 때 표시용 |
| `supportContent` | 지원 내용 원문. KINFA 정책은 `대출 한도: ...`, `금리: ...`처럼 "라벨: 값" 줄을 줄바꿈(`\n`)으로 묶은 값. 원문이 없으면 `null`. 숫자 값(`benefitAmount`)이 없을 때 표시용 |
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
        "simulationBenefit": null,
        "ageCondition": null,
        "incomeCondition": "중위소득 150% 이하",
        "supportContent": "월 최대 20만원, 최장 12개월 지원",
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
| `policyId` 누락 | 400 | `정책을 선택해주세요.` |
| `status` 누락, 공백 | 400 | `정책 상태를 입력해주세요.` |
| `status`가 허용값이 아님 | 400 | `유효하지 않은 정책 상태입니다. (APPLIED, INTEREST, ABANDONED 중 하나)` |
| 존재하지 않는 `policyId` | 400 | `존재하지 않는 정책입니다.` |
| 이미 등록한 정책 | 409 | `이미 등록된 정책입니다.` |

> `status`는 `APPLIED` \| `INTEREST` \| `ABANDONED` 중 하나이며, 대소문자와 앞뒤 공백은 구분하지 않습니다.

### 내 정책 삭제
- **DELETE** `/api/policies/my/{userPolicyId}`
- Path Parameter: `userPolicyId` (uuid)
- Response (200): `data: null`

| 상황 | 상태 | 메시지 |
|---|---|---|
| 존재하지 않거나 다른 사용자의 `userPolicyId` | 400 | `존재하지 않는 정책입니다.` |

> 다른 사용자의 정책 ID로 요청해도 없는 정책과 같은 응답을 주어, 그 정책의 존재 여부를 알 수 없게 합니다.

### 정책 AI 질문
- **POST** `/api/policies/{policyId}/ai-chat`
- 정책 1건의 저장된 원문과 사용자 정보(나이·소득 구간, 거주 지역, 주택 보유 여부, 취업 상태)를 근거로 답함. 원문에 없는 내용은 답하지 않음
- 한 대화에서 질문은 10개까지, 마지막 질문 후 24시간이 지나면 대화가 만료됨. 응답까지 수 초 이상 걸릴 수 있음
- Request Body:
```json
{
  "question": "무주택 직장인인데 신청할 수 있나요?",
  "conversationId": null
}
```
- Response (200):
```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "conversationId": "uuid",
    "answer": "원문 기준으로 만 19~34세 무주택 청년이면 신청 대상에 해당할 수 있습니다.\n- 최종 자격은 담당 기관에서 확인해야 합니다.",
    "remainingTurns": 9,
    "sourceLimited": false,
    "applyUrl": "https://young.busan.go.kr/index.nm?menuCd=271"
  }
}
```

| 필드 | 설명 |
|---|---|
| `question` | 필수. 공백 제외 1자 이상, 500자 이하 |
| `conversationId` | 요청: 생략하면 새 대화, 응답의 값을 넣으면 이어서 질문 |
| `answer` | 일반 텍스트 답변. 마크다운 문법과 URL은 들어가지 않음. 결론을 먼저 쓰고, 추가 설명은 줄바꿈(`\n`) 뒤 `- `로 시작하는 항목(최대 3개)으로 나뉨 |
| `remainingTurns` | 이 대화에서 남은 질문 수 |
| `sourceLimited` | 저장된 원문이 부족해 답변이 부정확할 수 있음. `true`면 첫 답변 끝에 안내 문구가 붙음 |
| `applyUrl` | 정책 데이터에 저장된 신청 URL ("신청하러 가기" 버튼용). 답변 내용과 관계없이 항상 같은 값. 신청 URL이 없거나 http(s) 주소가 아니면 `null` (KINFA 정책은 항상 `null`) |

| 상황 | 상태 |
|---|---|
| 질문이 비었거나 500자를 넘음 | 400 |
| 정책이 없음, 대화가 없거나 만료됨 (다른 사용자·다른 정책의 대화 포함) | 404 |
| 대화의 질문 수 상한 도달, 같은 대화에 동시에 질문 | 409 |
| AI 응답을 가져오지 못함 | 502 |
| AI 기능을 지금 사용할 수 없음 | 503 |

### 정책 AI 대화 종료
- **DELETE** `/api/ai-chat/{conversationId}`
- 대화와 질문·답변 기록을 삭제함. Response (200): `data: null`. 대화가 없거나 다른 사용자의 대화면 404

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
    "goalTitle": "내 집 마련",
    "goalTargetAmount": 50000000,
    "goalCurrentAmount": 8000000,
    "goalDeadline": "2028-12-31",
    "monthlySaving": 500000,
    "expectedCompletionDate": "2028-03-15",
    "policyCompletionDate": "2027-11-20",
    "optimisticCompletionDate": "2027-09-10",
    "pessimisticCompletionDate": "2028-08-01",
    "successProbability": 0.73,
    "policySuccessProbability": 0.91,
    "createdAt": "2026-10-30T10:00:00"
  }
}
```

| 필드 | 설명 |
|---|---|
| `goalTitle`, `goalTargetAmount`, `goalCurrentAmount`, `goalDeadline` | 실행 당시 목표의 제목·목표 금액·모은 금액·마감일 (#186). 나중에 목표를 수정해도 바뀌지 않으므로, 기록이 어떤 조건에서 나온 결과인지 표시할 때 사용. 이 필드가 추가되기 전에 실행한 기록은 `null` |
| `expectedCompletionDate` | 예상 달성일 (중앙값, 50번째 백분위) |
| `optimisticCompletionDate` | 낙관적 시나리오 달성일 (10번째 백분위, 가장 빠른 쪽) |
| `pessimisticCompletionDate` | 비관적 시나리오 달성일 (90번째 백분위, 가장 늦은 쪽) |
| `policyCompletionDate` | 정책 혜택을 반영한 예상 달성일(중앙값). 지원 기간과 1회 지급을 반영해 한 달씩 계산. 혜택이 없으면 `null` |
| `successProbability` | 목표 마감일까지 목표 금액을 달성할 확률 (0.0~1.0). **정책 혜택은 반영하지 않은 값** |
| `policySuccessProbability` | 정책 혜택을 반영한 달성 확률 (0.0~1.0). `policyCompletionDate`와 같은 조건이라 그 값이 `null`이면 이 값도 `null` |

> 목표의 `deadlineDefaulted`가 `true`면 마감일이 사용자가 정한 날짜가 아니므로, `successProbability`는 화면에 노출하지 않는 것을 권장합니다.
> 낙관·비관 달성일과 성공 확률이 추가되기 전에 저장된 시뮬레이션 기록은 세 값이 `null`일 수 있습니다.
> `policySuccessProbability`가 추가되기 전(#163)에 저장된 기록도 이 값이 `null`입니다.
> 목표를 수정해도 `goalId`와 시뮬레이션 기록은 그대로 유지됩니다. 기록이 지워지는 것은 목표를 삭제할 때뿐입니다. 목표를 수정한 뒤에는 시뮬레이션을 다시 실행하도록 안내하는 것을 권장합니다.

#### 계산 방식 (#163)

- **정책을 반영하지 않는 결과**(예상·낙관·비관 달성일, `successProbability`)는 "매달 같은 금액을 넣을 때 필요한 개월 수" 공식으로 계산합니다.
  몬테카를로 1만 회로 월 저축액을 `입력값 ± 20%`(정규분포)로 뽑고, 그 결과의 10%·50%·90% 지점을 낙관·예상·비관 달성일로 씁니다.
- **정책을 반영하는 결과**(`policyCompletionDate`, `policySuccessProbability`)는 공식으로 지원 기간이 끝나는 시점과 1회 지급을 표현할 수 없어
  한 달씩 진행해 계산합니다. 한 달 안에서의 순서는 **이자 → 내 저축액 → 지원금(지원 기간 안이면) → 1회 지급(첫 달)** 입니다.
- 정책 원문에 월 지급 기간이 없으면 **12개월만** 반영합니다. 목표 달성까지 계속 받는다고 보면 달성일이 실제보다 빨라지기 때문입니다.
- 흔들림(±20%)은 **내 저축액에만** 적용합니다. 정해진 지원금까지 흔들면 낙관·비관 범위가 실제보다 넓어집니다.
  정책 반영 계산은 정책 미반영 계산과 **같은 저축액 표본**을 쓰므로, 정책을 넣었다고 기본 결과가 달라지지 않습니다.
- 달성까지 걸리는 개월 수는 **1,200개월(100년)** 을 넘지 않습니다.

예) 남은 금액 2,000만원, 월 30만원 저축, 이율 0, 월 20만원 × 24개월 지원
→ 24개월간 월 50만원으로 1,200만원, 남은 800만원을 월 30만원으로 27개월 = **51개월**
(지원 기간을 반영하지 않고 계속 더하면 40개월로 잘못 계산됩니다)

> `policyCompletionDate`·`policySuccessProbability`가 `null`인 경우
> - 요청에 `userPolicyId`를 보내지 않은 경우
> - 보낸 정책에 금액으로 환산된 혜택이 없는 경우 (대출·현물·서비스 정책, 혜택 금액이 아직 정리되지 않은 정책).
>   이때도 요청은 성공(200)하고 나머지 결과는 정상으로 계산됩니다.
>
> 혜택 금액은 정책 동기화 후 AI가 원문에서 추출해 채웁니다 (#162). 정책 목록의 `simulationBenefit`이 `null`이 아닌 정책만 두 값이 계산되므로,
> 프론트는 시뮬레이션 전에 정책 반영 결과가 나올지 미리 알 수 있습니다.
> 프론트는 `null`일 때 정책 반영 영역을 숨기거나 "이 정책은 저축 달성일에 반영되지 않습니다" 같은 안내로 대체해야 합니다.
> `simulationBenefit.maximum`이 `true`면 "최대 지원 금액 기준 예상"임을 함께 표시해야 합니다.

- 요청 필드 검증 규칙:

| 필드 | 필수 | 규칙 | 실패 시 메시지 |
|---|---|---|---|
| `goalId` | O | 본인 소유 목표의 ID | 누락: `목표를 선택해주세요.` / 없거나 타인 목표: `존재하지 않는 목표입니다.` |
| `monthlySaving` | O | 0보다 큰 정수(원) | 누락: `월 저축액을 입력해주세요.` / 0·음수: `월 저축액은 0보다 커야 합니다.` |
| `userPolicyId` | X | 본인이 스크랩한 정책 ID. 생략하거나 정책에 혜택 금액이 없으면 `policyCompletionDate`·`policySuccessProbability`가 `null` | `존재하지 않는 유저 정책입니다.` |
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

### 카테고리 값
앱(iOS)과 가상 금융 서버(mock-bank)는 아래 6개 한글 값을 그대로 `category`로 보냅니다.

`식비`, `교통`, `쇼핑`, `문화/여가`, `생활`, `기타`

- 서버는 값 목록을 제한하지 않고 받은 문자열을 그대로 저장·집계합니다. 다른 표기(예: `FOOD`, `문화생활`)를 섞어 보내면 같은 지출이 다른 카테고리로 나뉘어 통계와 이상 지출 넛지가 틀어지므로, 위 6개 값만 사용해 주세요.
- 조회·통계 응답의 `category`도 저장된 값 그대로 내려옵니다.

### 지출 동기화
- **POST** `/api/expenses/sync`
- Request Body: 배열 (빈 배열이면 아무것도 하지 않음)
```json
[
  {
    "amount": 50000,
    "category": "식비",
    "description": "점심",
    "spentAt": "2026-10-12"
  }
]
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `amount` | O | 지출 금액(원). 0보다 커야 함 |
| `category` | O | 카테고리 문자열(공백만으로 된 문자열 불가). [카테고리 값](#카테고리-값) 6개 중 하나 사용 |
| `description` | X | 메모 |
| `spentAt` | O | 지출일 `yyyy-MM-dd` |

- **중복 방지**: `category`, `amount`, `spentAt`, `description` 네 값이 모두 같은 지출은 이미 저장돼 있으면 건너뜁니다. 같은 목록을 여러 번 보내도 중복 저장되지 않으므로 재시도해도 안전합니다.
- Response (200): `data: null`

- 목록 중 **한 항목이라도** 규칙에 맞지 않으면 전체를 저장하지 않고 400으로 응답합니다(메시지는 첫 번째로 발견된 문제 하나).

| 상황 | 상태 | 메시지 |
|---|---|---|
| `amount` 누락 | 400 | `지출 금액을 입력해주세요.` |
| `amount` 0 이하 | 400 | `지출 금액은 0보다 커야 합니다.` |
| `category` 누락, 공백 | 400 | `지출 카테고리를 입력해주세요.` |
| `spentAt` 누락 | 400 | `지출 날짜를 입력해주세요.` |
| 목록에 `null` 항목 | 400 | `지출 내역에 빈 항목(null)이 있습니다.` |

### 지출 전체 조회
- **GET** `/api/expenses`
- 없으면 빈 배열. 정렬 순서는 보장하지 않으므로 필요하면 `spentAt`으로 정렬해 주세요.
- `description`은 항상 문자열입니다. 메모가 없으면 `null`이 아니라 빈 문자열(`""`)로 내려갑니다.
- Response (200):
```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "expenseId": "uuid",
      "amount": 50000,
      "category": "식비",
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
- 에러: `start`가 `end`보다 늦으면 400 `시작일이 종료일보다 늦을 수 없습니다.`

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
      { "category": "식비", "amount": 200000 },
      { "category": "교통", "amount": 120000 }
    ]
  }
}
```
- `byCategory`: 기간 내 지출이 없는 카테고리는 포함되지 않습니다. 정렬 순서는 보장하지 않습니다. 기간 내 지출이 없으면 `totalAmount: 0`, `byCategory: []`
- 에러: `start`가 `end`보다 늦으면 400 `시작일이 종료일보다 늦을 수 없습니다.`

> 기간별 조회와 통계에서 `start`·`end`가 빠지면 400 `필수 파라미터가 없습니다: start`, 날짜 형식이 틀리면 400 `파라미터 형식이 올바르지 않습니다: start`로 응답합니다.

### 은행 거래 웹훅 수신 (서버 간, 앱에서는 호출하지 않음)
- **POST** `/api/webhooks/bank-transactions`
- 가상 금융 서버(mock-bank)가 발생시킨 거래를 해당 사용자의 지출로 저장합니다.
- **인증**: 사용자 JWT가 아니라 `X-Bank-Webhook-Key` 헤더로 인증합니다. 값은 서버 환경변수 `BANK_WEBHOOK_KEY`와 같아야 합니다.
  - 헤더가 없거나 값이 다르면 403 `웹훅 키가 올바르지 않습니다.`
  - 서버에 키가 설정되지 않았으면 403 `웹훅 키가 서버에 설정되어 있지 않습니다.`
  - 사용자 JWT나 관리자 키(`X-Admin-Key`)로는 호출할 수 없습니다.
- Request Body: 배열 (최대 1000건, 빈 배열이면 빈 결과)
```json
[
  {
    "transactionId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "amount": 12000,
    "category": "식비",
    "description": "김밥천국",
    "spentAt": "2026-11-02"
  }
]
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `transactionId` | O | 가상 금융 서버의 거래 ID(UUID). 결과를 짝지어 돌려주는 데만 쓰며 저장하지 않음 |
| `userId` | O | 핀타겟 사용자 ID(UUID). 존재하는 사용자여야 함 |
| `amount` | O | 지출 금액(원). 0보다 커야 함 |
| `category` | O | 카테고리 문자열(공백만으로 된 문자열 불가). [카테고리 값](#카테고리-값) 6개 중 하나 사용 |
| `description` | X | 가맹점명 등 메모 |
| `spentAt` | O | 지출일 `yyyy-MM-dd` |

- **중복 방지**: 지출 동기화와 같은 기준입니다. 같은 사용자의 `category`, `amount`, `spentAt`, `description`이 모두 같은 지출이 이미 있거나, 같은 요청 안에서 앞서 나왔으면 저장하지 않고 `DUPLICATE`로 돌려줍니다. 같은 거래를 다시 보내도 안전합니다.
- Response (200): 거래별 결과를 요청 순서대로 반환
```json
{
  "status": 200,
  "message": "OK",
  "data": [
    { "transactionId": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "result": "CREATED" },
    { "transactionId": "1b4e28ba-2fa1-11d2-883f-0016d3cca427", "result": "DUPLICATE" }
  ]
}
```

| `result` | 의미 |
|---|---|
| `CREATED` | 새 지출로 저장됨 |
| `DUPLICATE` | 같은 지출이 이미 있어 건너뜀 |

- 목록 중 **한 항목이라도** 규칙에 맞지 않거나 존재하지 않는 사용자가 있으면, 전체를 저장하지 않고 400으로 응답합니다.

| 상황 | 상태 | 메시지 |
|---|---|---|
| `transactionId` 누락 | 400 | `거래 ID를 입력해주세요.` |
| `userId` 누락 | 400 | `사용자 ID를 입력해주세요.` |
| `amount` 누락 / 0 이하 | 400 | `지출 금액을 입력해주세요.` / `지출 금액은 0보다 커야 합니다.` |
| `category` 누락, 공백 | 400 | `지출 카테고리를 입력해주세요.` |
| `spentAt` 누락 | 400 | `지출 날짜를 입력해주세요.` |
| 목록에 `null` 항목 | 400 | `거래 목록에 빈 항목(null)이 있습니다.` |
| 1000건 초과 | 400 | `거래는 한 번에 최대 1000건까지 보낼 수 있습니다.` |
| 존재하지 않는 사용자 | 400 | `존재하지 않는 사용자입니다: {userId}` |

> UUID·날짜 형식이 틀리거나 JSON 형식이 깨진 요청은 400 `요청 본문의 형식이 올바르지 않습니다. …`로 응답합니다.

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

## 9. 실시간 이벤트 (SSE)

앱이 켜져 있는 동안 서버의 변경을 새로고침 없이 받기 위한 연결입니다. 앱이 백그라운드이거나 종료된 상태에서는 받을 수 없습니다(푸시 알림 아님).

### 이벤트 구독
- **GET** `/api/events/stream`
- 인증 필요 (`Authorization: Bearer {accessToken}`), `Accept: text/event-stream`
- 응답은 끊기지 않고 계속 이어지는 텍스트 스트림(UTF-8)입니다. 이벤트는 빈 줄로 구분됩니다.

```
event:connected
data:{"userId":"3fa85f64-5717-4562-b3fc-2c963f66afa6"}

:heartbeat

event:expense-created
data:{"expenses":[{"expenseId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","amount":15000,"category":"식비","description":"스타벅스","spentAt":"2026-11-04","createdAt":"2026-11-04T14:30:00"}]}
```

| 이벤트 | 언제 | data |
|---|---|---|
| `connected` | 연결 직후 한 번 | `{"userId": "..."}` |
| `expense-created` | 은행 거래(mock-bank)로 내 지출이 새로 저장됐을 때. 웹훅 한 번에 여러 건이면 한 이벤트로 묶어서 옴 | `{"expenses": [...]}`. 원소는 지출 조회 응답과 같은 형식 |
| `:heartbeat` | 30초마다. `:`로 시작하는 주석 줄이라 이벤트가 아니며 무시하면 됨 | 없음 |

- `expense-created`를 받으면 홈·지출 목록·통계·넛지를 다시 조회하세요. 이상 지출 넛지는 넛지 목록을 조회할 때 만들어집니다.
- 이벤트는 DB 저장이 끝난 뒤에 보내므로, 받자마자 조회해도 새 지출이 보입니다.
- 중복이라 저장되지 않은 거래, 앱이 직접 동기화(`/api/expenses/sync`)한 지출은 이벤트가 오지 않습니다.
- 연결은 최대 30분 유지되고, 서버 재배포나 네트워크 변경 때도 끊깁니다. 끊기면 몇 초 뒤 다시 연결하세요. 토큰이 만료됐으면 갱신 후 연결합니다.
- 같은 계정은 최대 5개까지 동시에 연결되며, 넘으면 가장 오래된 연결이 닫힙니다.

| 상황 | 상태 |
|---|---|
| 토큰 없음, 유효하지 않거나 만료된 토큰 | 401 (JSON 에러 응답) |

---

## 공통 에러 응답

모든 에러는 `{ "status": 상태코드, "message": "안내 문구", "data": null }` 형식입니다.

| 상태코드 | 설명 | 메시지 예시 |
|---------|------|------|
| 400 | 입력값 검증 실패 | API별 메시지 (예: `목표 제목을 입력해주세요.`) |
| 400 | JSON 문법 오류, 날짜·UUID 형식 오류 | `요청 본문의 형식이 올바르지 않습니다. (JSON 문법, 날짜는 yyyy-MM-dd, ID는 UUID 형식)` |
| 400 | 필수 쿼리 파라미터 누락 | `필수 파라미터가 없습니다: start` |
| 400 | 쿼리·경로 파라미터 형식 오류 | `파라미터 형식이 올바르지 않습니다: userPolicyId` |
| 401 | 인증 실패 (토큰 없음/만료) | `인증이 필요합니다. 토큰을 확인해주세요.` |
| 404 | 없는 API 경로 | `요청한 API를 찾을 수 없습니다.` |
| 405 | 지원하지 않는 HTTP 메서드 | `지원하지 않는 HTTP 메서드입니다: PATCH` |
| 415 | 요청 본문이 JSON이 아님 | `Content-Type은 application/json이어야 합니다.` |
| 500 | 서버 오류 (내부 정보는 응답에 담지 않음) | `서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.` |

### 정책 혜택 AI 추출 (관리자, 앱에서는 호출하지 않음)

```
POST /api/admin/policy/benefit/extract?all=false
X-Admin-Key: {ADMIN_API_KEY}
```

- 저장된 정책 원문에서 시뮬레이션에 반영할 현금 혜택을 AI로 추출해 저장합니다 (#162). 정책 동기화(`POST /api/admin/policy/sync`, 매일 03:00) 직후에도 자동 실행됩니다.
- `all=false`(기본): 아직 추출하지 않았거나 원문·프롬프트 버전이 바뀐 정책만 처리. `all=true`: 전체 다시 추출
- KINFA 정책은 AI를 호출하지 않고 대출(`LOAN`)로 분류합니다.
- 정책 수에 따라 수 분이 걸릴 수 있습니다. 이미 실행 중이면 409

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "targetCount": 220,
    "savedByType": { "CASH": 80, "LOAN": 40, "SERVICE": 90, "UNKNOWN": 10 },
    "failedCount": 0,
    "skippedCount": 0,
    "elapsedMs": 95000
  }
}
```

| 필드 | 설명 |
|---|---|
| `savedByType` | 유형별 저장 건수. `CASH`만 시뮬레이션에 반영 (`SAVINGS_MATCH`, `LOAN`, `CORPORATE`, `SERVICE`, `UNKNOWN`은 반영 안 함) |
| `failedCount` | AI 호출·응답 해석에 실패해 기존 값을 유지한 정책 수. 다음 동기화 때 다시 시도 |
| `skippedCount` | AI를 쓸 수 없는 상태(키 없음, 호출 한도 초과, 인증 실패)여서 중간에 멈춰 시도하지 않은 정책 수 |

### 정책 원문 청크 갱신 (관리자, 앱에서는 호출하지 않음)

```
POST /api/admin/policy/chunk/refresh?all=false
X-Admin-Key: {ADMIN_API_KEY}
```

- 전체 정책 검색(통합 RAG)에 쓸 정책 원문 청크를 만듭니다 (#180). 정책 동기화(`POST /api/admin/policy/sync`, 매일 03:00) 직후에도 자동 실행되며, 배포 직후 기존 정책 청크를 바로 만들 때 씁니다.
- `all=false`(기본): 청크가 없거나 원문·분할 규칙이 바뀐 정책만 다시 만듦. `all=true`: 전체 다시 만듦
- AI를 호출하지 않습니다. 정책 동기화와 잠금을 공유하므로 동기화나 다른 청크 갱신이 실행 중이면 409

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "policyCount": 864,
    "regeneratedCount": 12,
    "skippedCount": 852,
    "chunkCount": 15,
    "orphanDeletedCount": 0,
    "elapsedMs": 850
  }
}
```

| 필드 | 설명 |
|---|---|
| `policyCount` | 검사한 정책 수 (전체 정책) |
| `regeneratedCount` | 청크를 새로 만든 정책 수 |
| `skippedCount` | 원문이 같아 그대로 둔 정책 수 |
| `chunkCount` | 새로 저장한 청크 수 |
| `orphanDeletedCount` | 정책 테이블에 없는 정책의 청크를 지운 건수 |

### 정책 청크 임베딩 갱신 (관리자, 앱에서는 호출하지 않음)

```
POST /api/admin/policy/embedding/refresh?all=false
X-Admin-Key: {ADMIN_API_KEY}
```

- 정책 청크를 OpenAI 임베딩(`text-embedding-3-small`, 1536차원)으로 변환해 `policy_chunk_embedding`에 저장합니다 (#181). 정책 동기화 직후(청크 갱신·혜택 추출 다음)에도 자동 실행되며, 배포 직후 기존 청크를 바로 임베딩할 때 씁니다.
- 사전 조건: `docs/sql/181_policy_chunk_embedding.sql` 실행 (pgvector 확장, 테이블 생성)
- `all=false`(기본): 임베딩이 없거나 다른 모델로 만든 청크만. `all=true`: 전체 다시 임베딩
- 청크 100개씩 한 요청으로 보냅니다 (`policy.embedding.batch-size`). `OPENAI_API_KEY`가 없거나 호출 한도를 넘으면 그 시점부터 건너뜁니다(`skippedCount`)
- 정책 동기화와 잠금을 공유하므로 동기화·청크 갱신·다른 임베딩 갱신이 실행 중이면 409

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "model": "text-embedding-3-small",
    "targetCount": 15,
    "embeddedCount": 15,
    "failedCount": 0,
    "skippedCount": 0,
    "elapsedMs": 2300
  }
}
```

| 필드 | 설명 |
|---|---|
| `model` | 임베딩 모델 |
| `targetCount` | 임베딩 대상 청크 수 |
| `embeddedCount` | 임베딩을 저장한 청크 수 |
| `failedCount` | 호출·저장에 실패한 청크 수 (다음 실행 때 다시 시도) |
| `skippedCount` | AI를 쓸 수 없어 건너뛴 청크 수 |

---

## 참고 사항

- 운영 서버 주소: `http://54.180.93.46:8080` (HTTPS 미적용)
- 스웨거 UI: `{서버 주소}/swagger-ui.html`
- 스웨거 JSON: `{서버 주소}/v3/api-docs`
- JWT 토큰 만료 시간: 1시간 (3600초)
- 서버 기준 시간대: 한국 시간(Asia/Seoul). 목표 D-day, 넛지의 "오늘"·"이번 달"은 한국 날짜 기준으로 계산합니다.