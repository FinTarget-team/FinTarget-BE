# 시뮬레이션 주거 정책 월 절감액 추정 설계 (#257)

> 조사·설계 문서. **구현 완료 (2026-11-25)**. 구현에서 설계와 달라진 점은 10절에 적었다.
> 로컬 DB·실호출이 필요한 확인은 2026-11-25에 모두 끝내고 결과를 이 문서에 남겼다 (기준 브랜치: dev `1157d8a`, #245·#253 머지 후).

## 1. 목표와 범위

- 시뮬레이션은 `benefit_type = CASH`이고 지급액이 정해진 정책만 반영한다 (`Policy.hasBenefitForSimulation()` → `PolicyBenefit.hasAny()` → `BenefitType.reflectsInSimulation()`).
- 서울 무주택 학생 추천 목록 6~9위의 주거 정책 4개는 실제로 주거비를 줄여 주지만 금액이 없어(`SERVICE`) 시뮬레이션에 반영되지 않는다.
- 공식 기준값으로 **월 절감액을 추정**해, 이 정책을 스크랩하고 시뮬레이션하면 "정책 반영 달성일"이 나오게 한다.
- 추천 정렬·리랭킹·캐시, 혜택 추출 프롬프트, DB 스키마는 바꾸지 않는다. 추정은 실제 지급액(CASH)과 구분해서 응답에 표시한다.

## 2. 대상 정책 (로컬 DB 확인 결과)

4개 모두 출처 `YOUTH_CENTER`(온통청년), `benefit_type = SERVICE`, 혜택 금액 없음, 조건 추출의 `benefit_kind = COST_SAVING`, 현재 `simulationBenefit = null`.

| 정책명 (정확한 이름) | 외부 ID (plcyNo) | 지역 | 원문 근거 문장 | 적용 규칙 | 2003년생 (150만원 미만) 월 절감액 | 2005년생 (150~260만원) 월 절감액 | 예상 단축 (두 케이스) |
|---|---|---|---|---|---|---|---|
| 대학생 연합생활관(은행권,고양) | 20260123005400112079 | 전국 (region 없음) | 지원 내용 "기숙사비: 월 15만원(보증금 15만원)" | 기준 월세 − 원문 월 15만원 | 47만원 | 47만원 | 17개월 → 7개월, 약 43주 (10개월) |
| 기숙사형 청년주택 공급 | 20260922005400113506 | 16개 시·도 (SEOUL 포함) | 설명 "시세보다 저렴한 보증금/월세의 기숙사형 임대주택 공급" (비율 없음) | 기준 월세 × 50% | 31만원 | 31만원 | 17개월 → 9개월, 약 35주 (8개월) |
| 2026 청년안심주택 공급(매입) | 20261021005400214219 | SEOUL | 설명 "민간 건설 임대주택 일부를 청년안심주택으로 매입, 청년ㆍ신혼부부에게 공급", 지원 내용 "쾌적하고 저렴한 주택을 공급" (비율 없음) | 기준 월세 × 50% | 31만원 | 31만원 | 17개월 → 9개월, 약 35주 (8개월) |
| 청년 맞춤형 공적 임대주택 공급(통합공공임대) | 20260921005400113460 | 전국 (region 없음) | 설명 "무주택 청년 등에게 임대주택을 저렴한 임대료로 공급", 소득 조건 "기준 중위소득 150% 이하" (비율 없음) | 기준 월세 × (1 − 소득 구간 비율) | 31만원 (비율 50%) | 21.7만원 (비율 65%) | 2003년생 17 → 9개월 (약 35주), 2005년생 17 → 10개월 (약 30주) — 로컬 확인에서 일치 |

- **원문 우선 판단**: 원문에 임대료가 숫자로 있는 정책은 연합생활관(월 15만원)뿐이며 기준값 규칙과 같다. 나머지 3개는 "저렴한" 표현만 있고 비율·금액이 없어 기준값 규칙을 쓴다.
- **기간**: 4개 모두 원문에 지급(거주) 기간이 없어 기존 기본값 12개월(`PolicyBenefit.DEFAULT_MONTHS_WHEN_UNSPECIFIED`)을 쓴다. 연합생활관 원문의 "학기별 모집"은 모집 주기라 기간으로 쓰지 않는다.
- **예상 단축 계산 조건**: 목표 500만원, 현재 0원, 월 30만원, 연 3%(기본값). `SimulationService.monthsNeededWithBenefit`과 같은 식을 평균 저축액으로 계산했다. 같은 조건에서 CASH 정책 "2026 청년 월세 지원"(월 20만원 × 12개월)은 실제 API 결과가 17 → 10개월(약 30주)이고, 이 계산도 17 → 10개월로 같다. 주 수는 개월 차이 × 30.44 / 7이다. 앱은 월 단위 날짜(`plusMonths`)로 표시하므로 화면 차이는 개월 수 그대로 나온다.

### 2.1 현재 값 측정 (2026-11-25, 로컬 실호출)

| 케이스 | 4개 정책 스크랩 응답 `simulationBenefit` | 시뮬레이션 `expectedCompletionDate` | `policyCompletionDate` | 참고: 2026 청년 월세 지원 (CASH) |
|---|---|---|---|---|
| 2003년생, 150만원 미만 | 4개 모두 null | 2028-04-25 | 4개 모두 null | 2027-09-25 (정책 반영 성공 확률 0.93) |
| 2005년생, 150~260만원 | 4개 모두 null | 2028-04-25 | 4개 모두 null | 2027-09-25 (정책 반영 성공 확률 0.93) |

- 목표 마감은 온보딩 기본값 2027-11-25이고, 정책 없는 성공 확률은 약 0.03이다.

### 2.2 추천 목록 순위 (서울 학생 무주택)

| 정책 | 2003년생 (150만원 미만) | 2005년생 (150~260만원) |
|---|---|---|
| 2026 청년안심주택 공급(매입) | 7위 | 6위 |
| 기숙사형 청년주택 공급 | 8위 | 7위 |
| 청년 맞춤형 공적 임대주택 공급(통합공공임대) | 9위 | 8위 |
| 대학생 연합생활관(은행권,고양) | 10위 밖 | 10위 밖 |

- 1~4위는 #245 고정 노출(국가장학금, 희망두배, 청년미래적금, 서울 청년 월세 지원)이다. 위 순위는 리랭킹 결과다.
- 리랭킹이 20초 제한을 넘겨 규칙 대체 결과가 나간 첫 조회에서는 4개 모두 10위 밖이었다. 재시도한 리랭킹 결과가 캐시에 저장된 뒤 다시 조회해 위 순위를 얻었다.
- 연합생활관은 추천 10건에는 없지만 검색·통합 답변·스크랩으로 시뮬레이션할 수 있으므로 대상에 넣는다.

### 2.3 같은 유형의 다른 정책 (포함 여부 제안)

| 정책명 | 외부 ID | 지역 | 원문 금액·비율 | 제안 |
|---|---|---|---|---|
| 청년층 대상 공공임대주택 공급(통합공공임대) | 20260922005400113532 | 전국 | "소득에 연계한 임대주택을 저렴한 임대료로 공급" | **포함 (구현 완료)** — 같은 통합공공임대 제도, 같은 규칙 |
| 청년 맞춤형 공적 임대주택 공급(매입임대) | 20260922005400113536 | 전국 | "저렴한 보증금과 임대료" (비율 없음) | 제외 (확정 기준값에 매입임대 비율 없음) |
| 청년층 임대주택 공급 확대(통합공공임대주택) ×2 | 20250718005400211417, 20260406005400212509 | 인천 | 없음 | 제외 (기준 월세가 서울 대학가 값) |
| 부산 행복주택 및 통합공공임대주택 | 20260928005400213594 | 부산 | 없음 | 제외 (지역) |
| 청년층을 위한 매입임대주택 공급 | 20261030005400214640 | 경기 | 없음 | 제외 (지역, 비율 없음) |
| 대구행복기숙사 운영 | 20261103005400214708 | 대구 | 기숙사비 월 27.3만원 | 제외 (지역 기준 월세 필요, 후속 과제) |
| (광양시)광양학사(서울 공공기숙사) 운영 | 20260318005400212189 | 광주·전남 | 월 12만원 | 제외 (전남 출신 대상, 후속 과제) |
| 부산 청년 맞춤형 민간임대주택 공급(희망더함주택) | 20260429005400212905 | 부산 | 주변 시세 85% 이하 | 제외 (지역) |

## 3. 기준값과 출처 (확정안, 검증 결과)

| 기준값 | 값 | 검증 |
|---|---|---|
| 기준 월세 | 62만원 | 다방, 서울 주요 10개 대학 인근 원룸(보증금 1000만원·전용 33㎡ 이하) 평균 월세 62.2만원 (2026년 1월 기준, 2026-02 보도). 2026년 7월 기준은 62.5만원 |
| 대학생 연합생활관 | 원문 월 15만원 | 원문 "기숙사비: 월 15만원(보증금 15만원)"과 일치 |
| 기숙사형 청년주택 | 시세의 50% | 정부·언론 자료 "주변 시세의 50% 이하"(일부 30~50%, 40~50%). 50%는 상한이라 절감액을 적게 잡는 값 |
| 청년안심주택(매입, 공공임대) | 시세의 50% | 서울시 설명자료 "공공임대는 주변시세의 30~50%". 50%는 상한이라 절감액을 적게 잡는 값 |
| 통합공공임대 소득 구간 비율 | 150만원 미만 50%, 150~260만원 65%, 260~300만원 80%, 300만원 이상 90% | 2021년 행정예고안 "중위 50~70% → 시세 50%, 70~100% → 65%, 100~130% → 80%, 130~150% → 90%". 현행 고시(국토교통부고시 제2024-720호)의 최종 계수는 원문을 확인하지 못함. **300만원 이상을 80%에서 90%로 고쳐**, 중위 130% 이상 사용자의 절감액을 실제보다 크게 잡던 구간을 없앴다 (구현 시 결정) |

**소득 구간 매핑 검증** (저장소의 `MedianIncome.ONE_PERSON`: 2026년 1인 가구 월 2,564,238원)

- 150만원 미만 → 중위 58.5% 미만 → 50%: 중위 30% 미만은 실제 35%, 30~50%는 40%라 절감액을 적게 잡는 쪽(보수적)
- 150~260만원 → 58.5~101.4% → 65%: 150~179만원(58.5~70%)은 실제 50%, 256~260만원은 80%라 경계에서 조금 다름
- 260~300만원 → 101.4~117% → 80%: 행정예고안의 100~130% 구간과 맞음
- 300만원 이상 → 117% 이상 → 90%: 333만원(130%) 이상이 실제 90%라, 구현에서 이 구간을 90%로 두어 절감액을 크게 잡지 않게 했다. 384.6만원(150%) 초과는 자격 밖이지만 추정은 자격을 판단하지 않음
- 2027년 1인 가구 중위소득(2,736,042원)이 적용되면 경계 비율이 바뀌지만, 확정 규칙이 사용자 월 소득 구간 기준이라 그대로 둔다

## 4. 코드 조사 결과

| 항목 | 위치 | 내용 |
|---|---|---|
| 시뮬레이션 반영 여부 | `Policy.hasBenefitForSimulation()` → `PolicyBenefit.hasAny()` | `type`이 CASH가 아니면 false |
| 반영 유형 | `BenefitType.reflectsInSimulation()` | `this == CASH` |
| 정책 반영 계산 | `SimulationService.runSimulation` → `monthsNeededWithBenefit(remaining, saving, rate, PolicyBenefit)` | `benefit.amountForMonth(month)`만 쓰므로, 메모리에서 만든 `PolicyBenefit`(DB 저장 안 함)도 그대로 계산에 넣을 수 있음 |
| 응답의 혜택 | `SimulationBenefitResponse.from(Policy)` | `PolicyResponse.from(Policy)`에서만 만들어짐 |
| `PolicyResponse.from` 사용처 | `PolicyService.getMatchingPolicies`(추천 목록), `UserPolicyResponse.from`(스크랩 목록 `GET /api/policies/my`, 스크랩 생성 `POST /api/policies/my`), `PolicySearchResponse.Item`(검색) | 모두 사용자 ID를 아는 서비스에서 호출됨 |
| 추천 정렬 영향 | `RuleBasedPolicyRecommendationSelector.candidate(...)`가 `policy.hasBenefitForSimulation()`을 정렬 키(`hasCashBenefit`)로 씀 | **`hasBenefitForSimulation()`을 바꾸면 추천 순서가 바뀜** → 바꾸지 않는다 |
| #245 리소스 장치 | `ManualPolicyCatalog` (`policy/manual-policies.json`의 `benefitOverrides`, `textOverrides`, `pins`) | 출처·외부 ID·정책명 일치(`sameTarget`) 확인 장치가 있음. `benefitOverrides`는 DB의 `benefit` 칸을 바꾸는 방식이라 CASH가 되면 추천 정렬까지 바뀜 |
| 사용자 소득 | `User.getIncome()` (대표값) → `IncomeBracket.fromIncome(income)` → `getMinIncome()`/`getMaxIncome()` | 시뮬레이션 시점에는 `SimulationService`에 `UserRepository`가 없어 추가 필요 |
| 중위소득 | `MedianIncome.ONE_PERSON` (2026, 2027) | 3절 검증에 사용. 추정 규칙 자체는 월 소득 구간 기준이라 직접 쓰지 않음 |
| 시뮬레이션 저장 | `Simulation` 엔티티 | 추정 여부를 저장할 칸이 없음 (스키마 변경 금지) → 실행 응답에만 표시 |

**가장 작은 변경 결론: 리소스 확장 + 별도 계산기**

- `benefitOverrides`를 써서 CASH로 덮는 방식은 쓰지 않는다. 추천 정렬이 바뀌고, 사용자 소득별 값(통합공공임대)을 DB 한 칸에 담을 수 없다.
- 리소스(`manual-policies.json`)에 `savingEstimates`를 추가하고, 작은 계산기(`SimulationSavingEstimator`)가 요청 시점에 사용자 소득으로 월 절감액을 계산한다.
- DB에 쓰지 않으므로 **운영 적용에 manual sync 재실행이 필요 없다** (배포만으로 적용). 운영에서 외부 ID가 맞는지 확인하려면 manual sync 응답에 확인 항목을 추가하는 것을 권장한다(선택, 7절).

## 5. 변경 설계

### 5.1 리소스: `src/main/resources/policy/manual-policies.json`에 `savingEstimates` 추가

```json
"savingEstimates": [
  {
    "source": "YOUTH_CENTER",
    "externalId": "20260123005400112079",
    "name": "대학생 연합생활관(은행권,고양)",
    "rule": "FIXED_RENT",
    "rent": 150000,
    "userRegions": ["SEOUL"],
    "basis": "서울 주요 대학가 원룸 평균 월세 62만원 대비 기숙사비 월 15만원",
    "sourceUrl": "정책 원문(온통청년) 지원 내용"
  },
  {
    "source": "YOUTH_CENTER",
    "externalId": "20260922005400113506",
    "name": "기숙사형 청년주택 공급",
    "rule": "RENT_RATIO",
    "ratio": 0.5,
    "userRegions": ["SEOUL"],
    "basis": "서울 주요 대학가 원룸 평균 월세 62만원의 50% 수준 임대료"
  },
  {
    "source": "YOUTH_CENTER",
    "externalId": "20261021005400214219",
    "name": "2026 청년안심주택 공급(매입)",
    "rule": "RENT_RATIO",
    "ratio": 0.5,
    "userRegions": ["SEOUL"],
    "basis": "서울 주요 대학가 원룸 평균 월세 62만원의 50% 수준 임대료 (공공임대 시세 30~50%)"
  },
  {
    "source": "YOUTH_CENTER",
    "externalId": "20260921005400113460",
    "name": "청년 맞춤형 공적 임대주택 공급(통합공공임대)",
    "rule": "INCOME_TIERED_RATIO",
    "tiers": [
      { "maxIncomeExclusive": 1500000, "ratio": 0.5 },
      { "maxIncomeExclusive": 2600000, "ratio": 0.65 },
      { "maxIncomeExclusive": null, "ratio": 0.8 }
    ],
    "userRegions": ["SEOUL"],
    "basis": "서울 주요 대학가 원룸 평균 월세 62만원과 소득 구간별 임대료 비율"
  }
]
```

- 2.3절 제안대로 "청년층 대상 공공임대주택 공급(통합공공임대)"(20260922005400113532)를 넣는다면 통합공공임대와 같은 항목을 하나 더 추가한다.
- `months`는 생략한다(원문에 기간이 없으면 12개월). 원문에 기간이 생기면 `months`로 지정한다.
- `userRegions`: 기준 월세가 서울 대학가 값이라 서울 사용자에게만 추정한다(다른 지역 사용자에게 과대 추정 방지). 비우면 전 지역이다.
- 규칙
  - `FIXED_RENT`: 월 절감액 = 기준 월세 − `rent` (0 이하면 추정 안 함)
  - `RENT_RATIO`: 월 절감액 = 기준 월세 × (1 − `ratio`)
  - `INCOME_TIERED_RATIO`: 사용자 소득 구간(`IncomeBracket`)의 `minIncome`이 들어가는 첫 tier의 `ratio`로 `RENT_RATIO`와 같이 계산. 소득이 없으면 추정 안 함
  - 금액은 원 단위로 반올림 (예: 620000 × 0.35 = 217000)

### 5.2 `ManualPolicyCatalog` (같은 파일 구조 확장)

- `Document` record에 `List<SavingEstimate> savingEstimates` 추가, 생성자에서 `orEmpty`로 받음 (`textOverrides`·`pins`와 같은 방식)
- `public record SavingEstimate(String source, String externalId, String name, String rule, Long rent, Double ratio, List<Tier> tiers, Integer months, List<String> userRegions, String basis, String sourceUrl)`
  - `matches(Policy)`: 기존 `sameTarget(source, externalId, name, policy)` 재사용
  - `validate()`: 출처·외부 ID·정책명 필수, MANUAL 금지, `rule`별 필수 값(`FIXED_RENT` → `rent > 0`, `RENT_RATIO` → `0 < ratio < 1`, `INCOME_TIERED_RATIO` → tier 1개 이상이고 마지막 tier의 `maxIncomeExclusive = null`, 상한 오름차순), `userRegions`는 `Region.fromCode`, `basis` 필수, 같은 대상 중복 금지
- `public List<SavingEstimate> savingEstimates()` 추가
- `FAIL_ON_UNKNOWN_PROPERTIES = true`라 리소스와 record 필드명이 정확히 같아야 기동된다

### 5.3 새 클래스 `kr.fintarget.api.domain.simulation.estimate.SimulationSavingEstimator` (@Component)

```java
public Optional<SavingEstimateResult> estimate(Policy policy, User user)
```

- 순서: 설정이 꺼져 있으면 empty → `policy.hasBenefitForSimulation()`이 true면 empty (실제 지급액이 우선) → `catalog.savingEstimates()`에서 `matches(policy)`인 항목 → `userRegions`가 있으면 `user.getRegion()` 포함 여부 → 규칙 계산 → 0 이하면 empty
- `record SavingEstimateResult(long monthlyAmount, Integer months, String basis)`
  - `PolicyBenefit toPolicyBenefit()`: `PolicyBenefit.builder().type(BenefitType.CASH).monthlyAmount(monthlyAmount).benefitMonths(months).maximum(false).evidence(basis).build()` (메모리에서만 쓰고 저장하지 않음)
- 생성자 인자: `ManualPolicyCatalog`, `@Value("${simulation.saving-estimate.enabled:true}") boolean enabled`, `@Value("${simulation.saving-estimate.base-rent:620000}") long baseRent`
- 사용자 소득: `IncomeBracket.fromIncome(user.getIncome())`. null이면 `INCOME_TIERED_RATIO`는 empty
- 테스트용 `static SimulationSavingEstimator disabled()` (기존 `ManualPolicyPinner.disabled()`와 같은 방식)

### 5.4 `SimulationService.runSimulation`

- 의존성 추가: `UserRepository userRepository`, `SimulationSavingEstimator savingEstimator` (`@RequiredArgsConstructor`라 필드만 추가)
  - `SimulationServiceTest`가 생성자를 직접 부른다 (56행, 247행: `new SimulationService(simulationRepository, goalRepository, userPolicyRepository, new Random(42L))`). 새 필드를 `random` 뒤에 두고, 두 곳에 `userRepository` mock과 `SimulationSavingEstimator.disabled()`(또는 테스트용 추정기)를 추가한다
- 기존 `if (policy.hasBenefitForSimulation()) { ... }` 블록을 아래처럼 바꾼다

```java
PolicyBenefit benefit = null;
SavingEstimateResult estimate = null;
if (policy.hasBenefitForSimulation()) {
    benefit = policy.getBenefit();
} else {
    User user = userRepository.findById(userId.toString()).orElse(null);
    estimate = user == null ? null : savingEstimator.estimate(policy, user).orElse(null);
    benefit = estimate == null ? null : estimate.toPolicyBenefit();
}
if (benefit != null) {
    PolicyBenefit applied = benefit;
    MonteCarloResult policyResult = runMonteCarlo(savingSamples, monthsToDeadline,
            saving -> monthsNeededWithBenefit(remaining, saving, monthlyRate, applied));
    policyDate = ...; policySuccessProbability = ...;
}
```

- 응답: `SimulationResponse.from(simulation)`에 추정 정보를 넘기는 오버로드 `from(Simulation, SavingEstimateResult estimate)`를 추가하고 `runSimulation`에서만 쓴다. 기록 조회(`getSimulations`, `getSimulationsByGoal`)는 저장된 칸이 없어 새 필드가 null이다

### 5.5 응답 DTO

- `SimulationBenefitResponse`
  - 필드 추가: `boolean estimated`, `String basis`(nullable). 기존 필드는 그대로
  - 기존 `from(Policy)`는 `estimated=false, basis=null`로 유지
  - 추가: `static SimulationBenefitResponse fromEstimate(SavingEstimateResult e)` → `monthlyAmount = e.monthlyAmount()`, `months = e.months() == null ? 12 : e.months()`, `monthsSpecified = e.months() != null`, `oneTimeAmount = null`, `maximum = false`, `estimated = true`, `basis = e.basis()`
- `PolicyResponse`
  - 기존 `from(Policy)` 유지 (추정 없음)
  - 추가: `from(Policy policy, SimulationBenefitResponse simulationBenefit)`
- 추정을 넣는 곳 (사용자를 아는 서비스에서 `estimator.estimate(policy, user)`로 만든 값을 넘김)
  - `PolicyService.getMatchingPolicies` (추천 목록): 이미 `User user`가 있음. 고정 노출·리랭킹 결과에 덧입히는 단계가 아니라 응답 변환 단계에서만 바꿈
  - `PolicyService.getUserPolicies`, `createUserPolicy` (스크랩 목록·생성): `UserPolicyResponse.from(UserPolicy, SimulationBenefitResponse)` 오버로드 추가, 사용자 조회 1회 추가
  - 검색(`PolicySearchResponse`)은 선택. 넣는다면 같은 방식
- `SimulationResponse`: 필드 추가 `Boolean policyBenefitEstimated`, `String policyBenefitBasis` (정책 반영 계산을 안 했거나 기록 조회면 null)
- **추천 정렬·리랭킹은 바뀌지 않는다** (`hasBenefitForSimulation()`과 DB `benefit`을 건드리지 않음). 다만 추천 목록 응답에서 `simulationBenefit`이 null이 아닌 건수는 늘어난다

### 5.6 프론트 전달용 응답 예시와 문구

```json
"simulationBenefit": {
  "monthlyAmount": 470000,
  "months": 12,
  "monthsSpecified": false,
  "oneTimeAmount": null,
  "maximum": false,
  "estimated": true,
  "basis": "서울 주요 대학가 원룸 평균 월세 62만원 대비 기숙사비 월 15만원"
}
```

- `estimated = true`면 금액 앞에 "예상 절감"을 붙이고, 지급액처럼 "받는 금액"으로 쓰지 않는다
  - 카드: "월 47만원 예상 절감 (12개월 기준)"
  - 상세 안내: "서울 주요 대학가 원룸 평균 월세 62만원 대비 기숙사비 월 15만원으로 계산한 예상 절감액이에요. 실제 임대료와 거주 기간에 따라 달라질 수 있어요."
- 시뮬레이션 결과(`policyBenefitEstimated = true`): "정책 반영 달성일은 예상 주거비 절감액을 저축에 더한 결과예요."
- 정책별 `basis` 예시
  - 기숙사형 청년주택: "서울 주요 대학가 원룸 평균 월세 62만원의 50% 수준 임대료"
  - 청년안심주택(매입): "서울 주요 대학가 원룸 평균 월세 62만원의 50% 수준 임대료 (공공임대 시세 30~50%)"
  - 통합공공임대: "서울 주요 대학가 원룸 평균 월세 62만원과 소득 구간별 임대료 비율"

### 5.7 설정과 compose

| 설정 | 환경 변수 | 기본값 | 설명 |
|---|---|---|---|
| `simulation.saving-estimate.enabled` | `SIMULATION_SAVING_ESTIMATE_ENABLED` | `true` | false면 추정 없이 기존 동작 (응답 `estimated`는 항상 false) |
| `simulation.saving-estimate.base-rent` | `SIMULATION_SAVING_ESTIMATE_BASE_RENT` | `620000` | 기준 월세(원) |

- `application-example.yml`

```yaml
simulation:
  saving-estimate:
    enabled: ${SIMULATION_SAVING_ESTIMATE_ENABLED:true}
    base-rent: ${SIMULATION_SAVING_ESTIMATE_BASE_RENT:620000}  # 다방 서울 주요 10개 대학가 원룸 평균 월세 (2026-01)
```

- `docker-compose.yml` app 환경 변수: `SIMULATION_SAVING_ESTIMATE_ENABLED: ${SIMULATION_SAVING_ESTIMATE_ENABLED:-true}`, `SIMULATION_SAVING_ESTIMATE_BASE_RENT: ${SIMULATION_SAVING_ESTIMATE_BASE_RENT:-620000}`
- `.env.example`에 두 변수를 빈 값과 설명으로 추가

## 6. 테스트 목록

### 6.1 단위 테스트로 확인 (로컬 DB·실호출 없이)

- `ManualPolicyCatalogTest`
  - 리소스의 `savingEstimates` 4건(포함 결정 시 5건) 로드, 외부 ID·정책명·규칙 확인
  - 잘못된 항목은 기동 시 막힘: 규칙별 필수 값 누락, `ratio` 범위 밖, tier 정렬·마지막 null 위반, 지역 코드 오류, MANUAL 출처, 중복 대상
- `SimulationSavingEstimatorTest` (Policy는 `Policy.create(..., externalId, "YOUTH_CENTER", PolicyText...)`, User는 `User.builder().region("SEOUL").income(IncomeBracket.X.getIncome())`)
  - 연합생활관 → 470000, `months` null
  - 기숙사형·안심주택 → 310000
  - 통합공공임대: `UNDER_150` → 310000, `RANGE_150_260` → 217000, `RANGE_260_300`·`OVER_410` → 124000, 소득 없음 → empty
  - 정책명이 다르거나 외부 ID·출처가 다르면 empty
  - 서울 외 지역 사용자 → empty (`userRegions`)
  - 설정 off → empty
  - 실제 CASH 혜택이 있는 정책(`hasBenefitForSimulation()=true`)은 empty
  - `base-rent`를 바꾸면 결과가 비례해서 바뀜
- `SimulationServiceTest`
  - `monthsNeededWithBenefit(5_000_000, 300_000, 0.03/12, 월 470000 × 12개월)` = 7, 310000 = 9, 217000 = 10, 기본 `monthsNeeded` = 17
  - 기존 테스트 기준값: 월 200000 × 12개월 = 10 (실데이터의 CASH 정책 결과와 같음)
  - `runSimulation`: SERVICE 정책 + 추정 있음 → `policyCompletionDate` not null, `policyBenefitEstimated=true`, `policyBenefitBasis` 일치. 추정 없음 → 기존처럼 null. CASH 정책 → 추정기를 부르지 않음
- `SimulationBenefitResponse` / `PolicyResponse`: 기존 `from(Policy)`는 `estimated=false`, `fromEstimate`는 `estimated=true`, `months=12`, `monthsSpecified=false`
- `PolicyServiceTest`: 추정기를 `@Spy SimulationSavingEstimator.disabled()`로 넣어 기존 테스트 유지(`ManualPolicyPinner`와 같은 방식). 켠 경우 추천 목록·스크랩 응답에 `estimated=true`가 나오고, **추천 순서는 끈 경우와 같음**
- 컨트롤러 직렬화: `estimated`, `basis`, `policyBenefitEstimated`, `policyBenefitBasis` 필드명

### 6.2 실데이터가 필요한 확인 (이번에 끝낸 것과 배포 후 할 것)

- 이번에 끝냄 (2절): 4개 정책의 외부 ID·정확한 이름·원문, 현재 응답(`simulationBenefit = null`, `policyCompletionDate = null`), 추천 순위, 같은 조건 CASH 정책 결과(17 → 10개월)
- 배포 후 (운영): 외부 ID·정책명이 운영 DB와 같은지 (7절), 서울 학생 계정에서 4개 정책 스크랩 → 시뮬레이션 결과가 6.1의 개월 수와 맞는지

## 7. 다른 PC에서 구현하는 순서

1. dev 최신에서 브랜치 생성 (이 문서 브랜치 `feature/257-simulation-saving-estimate`가 머지됐으면 dev, 아니면 이 브랜치)
2. 리소스에 `savingEstimates` 추가 (5.1) → `ManualPolicyCatalog` 확장 (5.2) → `ManualPolicyCatalogTest`로 로드·검증 확인
3. `SimulationSavingEstimator` (5.3) + 단위 테스트. 기대값은 6.1의 숫자
4. `SimulationService` 변경 (5.4) + `monthsNeededWithBenefit` 기대 개월 수 테스트
5. 응답 DTO (5.5) → `PolicyService`·`UserPolicyResponse` 연결 → `PolicyServiceTest`에서 추천 순서 불변 확인
6. 설정·compose·`.env.example` (5.7), `docs/API_Spec.md`의 `simulationBenefit`·시뮬레이션 응답 필드 설명 갱신
7. `./gradlew test` 전체 통과 확인. 로컬 DB가 없으면 `ddl-auto=validate` 기동은 dev 서버 배포 후 확인 (DB 스키마 변경이 없으므로 위험 낮음)
8. 로컬 DB 없이 결과를 검증하는 방법: 6.1 단위 테스트의 기대 개월 수(17/7/9/10)가 2절의 실데이터 측정과 같은 식에서 나왔으므로, 단위 테스트 통과로 실데이터 결과를 대신 확인할 수 있다

### (선택) manual sync 응답에 확인 항목 추가

- `ManualPolicySyncResult`에 `savingEstimates[]`(`source`, `externalId`, `name`, `found`, `nameMatches`)를 `pins`와 같은 방식으로 추가하면, 운영에서 외부 ID가 맞는지 API 한 번으로 확인할 수 있다
- 추정은 DB에 쓰지 않으므로 기능 적용에는 manual sync가 필요 없다. 이 확인 항목을 넣었을 때만 확인용으로 1회 호출한다 (바뀐 원문이 없으면 AI 호출 없음)

## 8. 운영 배포 후 절차

1. dev 머지 → CD 배포 완료 확인 (DB 스키마 변경 없음)
2. (선택 항목을 구현했다면) `POST /api/admin/policy/manual/sync` 1회 호출 → `savingEstimates[]`의 `found`·`nameMatches`가 모두 true인지 확인. false면 운영 정책번호가 다른 것이므로 리소스의 외부 ID를 고침
3. 서울 학생 계정으로 `GET /api/policies`: 4개 중 추천에 나온 정책의 `simulationBenefit.estimated = true`, 금액이 2절과 같은지
4. 연합생활관 등 스크랩 → `POST /api/simulations` (목표 500만원, 현재 0원, 월 30만원): `policyCompletionDate`가 나오고 `policyBenefitEstimated = true`인지
5. 문제가 있으면 `.env`에 `SIMULATION_SAVING_ESTIMATE_ENABLED=false` 후 `docker compose up -d --no-deps app` (재배포 없이 기존 동작으로 되돌림)

## 9. 알려진 한계

- 추정값이다. 실제 임대료는 단지·호실·소득 구간·보증금에 따라 다르고, 서울 대학가 평균 월세 기준이라 지역·주거 형태(본가 거주 등)에 따라 실제 절감액이 없을 수 있다. 응답의 `estimated`·`basis`로 화면에 반드시 구분해서 보여 준다
- 거주 기간을 원문에서 알 수 없어 12개월로 계산한다
- 통합공공임대의 구간 비율은 2021년 행정예고안 기준이며, 현행 고시의 최종 계수는 확인하지 못했다. 300만원 이상은 90%로 둬 절감액을 크게 잡지 않는다
- 추정은 자격을 판단하지 않는다 (예: 통합공공임대 중위소득 150% 초과 사용자도 계산됨). 추천 후보 조회는 기존대로 소득 조건을 거른다
- 시뮬레이션 기록 조회에는 추정 여부가 남지 않는다 (스키마 변경 금지)
- 리랭킹이 제한 시간을 넘기면 규칙 대체 결과가 나가고, 이때는 추정 대상 정책이 추천 10건에 없다 (2026-11-25 로컬 첫 조회: 리랭킹 응답 약 21초). 구현에서 제한을 20초 → 30초로 늘려 이 경우를 줄였다


## 10. 구현 결과와 설계와 달라진 점 (2026-11-25)

### 설계와 달라진 점

| 항목 | 설계 | 구현 | 이유 |
|---|---|---|---|
| `basis` | 리소스(JSON)에 문장 그대로 저장 | 리소스에서 빼고 **요청 시점에 생성** | 기준 월세(지역별 설정)와 통합공공임대 비율(사용자 소득별)이 요청마다 달라 고정 문장으로 담을 수 없다. 형식: `입주 시 {지역 기준} 원룸 평균 월세 {N}만원의 {비율}% 수준 임대료로 계산한 예상 절감액` (FIXED_RENT는 `… 대비 기숙사비 월 {N}만원으로 …`) |
| 통합공공임대 tier | 50 / 65 / 80% (3단) | **50 / 65 / 80 / 90%** (4단, 상한 150만·260만·300만·없음) | 중위 130% 이상 사용자의 절감액을 실제보다 크게 잡는 구간을 없앰 |
| 대상 정책 수 | 4건 | **5건** (2.3절 제안대로 `20260922005400113532` 포함) | 같은 통합공공임대 제도, 같은 규칙 |
| 노출 설정 | 없음 | `simulation.saving-estimate.expose-in-policy-responses` 추가 (기본 true) | 목록 응답에 넣을지를 시뮬레이션 반영과 따로 끌 수 있게 (앱의 "목표 달성일 확인하기" 버튼이 이 값에 달려 있음) |
| 실행 응답 생성 | `SimulationResponse.from(Simulation, SavingEstimateResult)` 오버로드 | `from(Simulation)`(기록 조회, 추정 필드 항상 null) / `fromRun(Simulation, SavingEstimateResult)`(실행)로 **이름을 나눔** | 기록 조회에서 실수로 `false`가 나가지 않게 (요구사항: 기록 조회는 두 필드 null) |
| 리랭킹 제한 시간 | (설계 범위 밖) | 20s → **30s** (`policy.recommend.rerank.timeout`) | 2절 측정에서 리랭킹 응답이 약 21초였고, 제한을 넘기면 추정 대상 4건이 추천 10건에서 빠진다. 진행 중 계산 대기(#243)도 같은 값을 쓴다 |
| manual sync 확인 항목 | 선택 | **구현** (`savingEstimates[]`: source·externalId·name·rule·userRegions·found·nameMatches) | 운영에서 외부 ID가 맞는지 API 한 번으로 확인 |

### 경기 사용자 적용: 하지 않음

공식·신뢰할 만한 출처(한국부동산원 통계, 다방·직방 공식 발표, 정부·지자체 자료)에서 **서울 기준값과 비교할 수 있는 경기도 원룸 평균 월세를 찾지 못했다.**

| 찾은 자료 | 쓰지 못한 이유 |
|---|---|
| 다방 원룸 월세 리포트 (dabangapp.com/news) | **서울만 발표한다.** 경기 전체 평균은 없음 |
| 한국부동산원 월세가격동향조사 (reb.or.kr) | 조사 대상이 단독·아파트·연립/다세대·오피스텔이고 **"원룸(전용 33㎡ 이하·보증금 1000만원 환산)" 집계가 아니다.** 서울 기준값(다방)과 조건이 달라 같은 규칙에 쓰면 절감액이 왜곡된다 |
| 경기도 오픈데이터 포털 단독/다가구 전월세 자료 (data.gg.go.kr) | **원자료(실거래 목록)**다. 직접 평균을 내면 공식 발표 수치가 아니고, 면적·보증금 조건을 서울 기준값과 맞추기 어렵다 |
| 집계 사이트의 "경기 원룸 63만원" 류 | 블로그·집계 사이트로 **허용 출처가 아니다** |

**2차 조사 (2026-11-25, 출처를 넓혀 다시 찾음)** — 조건이 서울 기준값과 완전히 같지 않아도 쓰기로 기준을 완화했으나, 그래도 쓸 수 있는 값이 없었다.

| 출처 | 기준 시점 | 찾은 값 | 쓰지 못한 이유 |
|---|---|---|---|
| 국토교통부 2025년 주거실태조사 | 2025 | 수도권 임차가구 RIR 중위 19.7%, 청년 임차가구 RIR 17.4% | **비율(RIR)만 발표하고 경기 월세 금액이 없다.** 소득 대비 비율이라 기준 월세로 바꿀 수 없다 |
| 한국부동산원 오피스텔가격동향조사 (R-ONE) | 2026 1분기 | 경기 오피스텔 "평균가격" 850천원 (전국 12,645천원) | **월 임대료가 아니다.** 전국 값이 1,264만원대인 것에서 보듯 보증금을 포함한 환산 가격 지표이고, 보증금 1000만원 기준 월세로 바꿀 수 없다 |
| 한국부동산원 월세가격동향조사 | 2026 | 경기 포함 수도권 조사 | 1차와 같음. **지수·변동률 중심이고 "원룸(전용 33㎡ 이하·보증금 1000만원 환산)" 평균 금액 집계가 아니다** |
| 다방 공식 보도자료 (dabangapp.com/news) | 2026-09 | 서울 원룸 72만원, 서울 주요 10개 대학가 62.5만원(2026-07) | **서울만 발표한다.** 경기·수도권 원룸 평균, 경기 대학가(수원·용인·안산) 집계 없음 |
| 경기도 1인가구 통계 (gg.go.kr) | 2025 | 1인가구 177만, 보증금 있는 월세 23.8%, 월평균 생활비 161.7만원 | **점유 형태 비율과 생활비 총액만 있고 월세 금액이 없다** |
| 2025년 경기도 주거종합계획 | 2025 | 주거 정책·공급 계획 | 월세 평균 금액 집계가 없다 |
| 언론 인용 "경기 아파트 평균 월세 119.7만원" (2026-10) | 2026 | 119.7만원 | **아파트**다. 원룸과 주거 형태·면적이 달라 같은 규칙에 쓰면 절감액이 크게 왜곡된다 |
| house.manuz.com, trendmetriclab.com 등 ("경기 원룸 45만원" 류) | 2026 | 45만원 | **집계 사이트로 허용 출처가 아니다** (공식 발표나 공공 통계가 아님) |

→ `userRegions`는 5건 모두 `["SEOUL"]`이고, 기준 월세 설정은 지역별 맵이 아니라 단일 키(`simulation.saving-estimate.base-rent`)로 뒀다.
경기 기준값을 확보하면 `SimulationSavingEstimator.REGION_LABELS`에 지역·근거 문구를 더하고 `userRegions`에 `GYEONGGI`를 넣으면 된다.
다음에 다시 찾을 때는 위 두 표의 출처를 다시 보지 말고, **다방·직방이 경기/수도권 원룸 평균을 새로 발표했는지**나
**국토교통부 실거래가 공개시스템 기반의 공식 집계(보증금 1000만원 환산 월세)가 생겼는지**만 확인하면 된다.

### 로컬 확인 결과 (임시 DB, 대상 정책 6건 시드, 목표 500만원·현재 0원·월 30만원·연 3%)

| 케이스 | 추천 `simulationBenefit` | 연합생활관 | 통합공공임대 | 참고: CASH 정책 |
|---|---|---|---|---|
| 서울 2003년생 (150만원 미만) | 6건 중 6건 (추정 5 + 실지급 1) | 2028-04-25 → 2027-06-25 (약 44주) | → 2027-08-25 (약 35주) | → 2027-09-25 (약 30주), `estimated=false` |
| 서울 2005년생 (150~260만원) | 6건 중 6건 | → 2027-06-25 (약 44주) | → 2027-09-25 (약 30주, 65% 비율) | → 2027-09-25 (약 30주) |
| 경기 2004년생 (150만원 미만) | 4건 중 **0건** | — | — | 시뮬레이션 추정 필드 모두 `null` |

- 설계 2절의 기대 개월 수(기본 17 → 연합생활관 7, 31만원 9, 21.7만원 10)와 **모두 일치**했다
- manual sync 응답의 `savingEstimates[]` 5건 모두 `found=true`, `nameMatches=true`
- `ddl-auto=validate`로 기동 확인 (스키마 변경 없음)
- `./gradlew test` 1,293건 통과 (skip 8건은 조건부 실호출 평가 테스트)

## 참고 출처

- 다방 대학가 원룸 월세: 한국경제 2026-02-24 "등록금보다 무섭다… 대학가 원룸 월세 평균 62만원" (https://www.hankyung.com/article/2026022499466), 세계일보 2026-08-11 (https://www.segye.com/newsView/20260811519635)
- 청년안심주택 공공임대 시세 30~50%: 서울시 설명자료 (https://mediahub.seoul.go.kr/archives/2010689), 내 손안에 서울 (https://mediahub.seoul.go.kr/archives/2009425)
- 기숙사형 청년주택 시세 50% 이하: 정책브리핑 (https://www.korea.kr/news/policyNewsView.do?newsId=148893980), 스마트투데이 (https://www.smarttoday.co.kr/ko-kr/articles/2477)
- 통합공공임대 소득 구간별 임대료(2021년 행정예고안): 메디게이트뉴스 (https://medigatenews.com/stand/36884), 국토교통부고시 제2024-720호 (https://www.law.go.kr)
