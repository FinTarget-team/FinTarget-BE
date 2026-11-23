## PR 제목: [Feature] 정책 추천 개선: 소득·주택·사업자 대상 필터, 빈 지역 보완, 정렬 (#220)

### ✍️ 변경 내용 (Description)
- 정책 원문에서 소득 기준·본인 소득 상한·무주택 필수 여부·신청 대상(개인/사업자)·신청 가능 지역을 AI로 추출해 Policy에 저장함
- 정책 추천에서 **확실히 해당하지 않는 정책만** 제외함 (본인 소득 상한 초과, 무주택 필수 × 유주택, 사업자 전용 × 비자영업). 조건을 모르거나 사용자 값이 없으면 제외하지 않음
- 지역이 빈(전국 취급) 정책은 원문에서 추출한 지역으로 판정함 (예: 전남 3개 대학 대상 "금연대학교 운영")
- 추천 목록을 지금 신청 가능 → 판정 불가 → 마감·시작 전, 사용자 지역 → 전국, 조건 확인된 정책 순으로 정렬함
- 온통청년 신청기간 구분 코드(aplyPrdSeCd)를 저장해 상시 정책을 "지금 신청 가능"으로 판정함
- **정책 목록 API의 요청·응답 형식은 바뀌지 않음** (목록 건수와 순서만 바뀜, 프론트 수정 없음)

### 🛠️ 작업 내용 상세 (Detailed Changes)
1. 신청기간 구분 코드 저장 (`3c4dbad`)
  - `PolicyText.applyPeriodCode`(apply_period_code) 추가, `YouthCenterPolicyMapper`가 aplyPrdSeCd를 저장 (0057001 특정기간, 0057002 상시, 0057003 마감)
  - 온통청년 API 확인 결과 상시 786건은 모두 신청 기간(aplyYmd)이 빈 값, 특정기간 259건은 모두 기간이 있음
2. 조건 추출 (`637ff3b`) — 혜택 추출(#162) 구조를 그대로 따름
  - `PolicyCondition`(embeddable): income_basis, income_max_monthly, housing_requirement, target_type, extracted_region, condition_prompt_version, condition_source_hash, condition_extracted_at (모두 nullable, enum은 STRING)
  - `ConditionSource`: 정책명·등록 지역·설명·자격·참여 제한·소득·나이·지원 내용·기타·신청 방법·문의처 (필드당 1,500자, 전체 6,000자, 뒤쪽부터 자름). SHA-256 해시로 원문 변경 판단
  - 프롬프트 `prompts/policy-condition-v1.md`: 금액은 원문 그대로(incomeMaxAmount + incomePeriod) 받고 월 환산은 서버에서 함. 애매하면 UNKNOWN·빈 값
  - `ConditionExtractionParser`: 정의되지 않은 enum → 그 필드만 UNKNOWN, 정의되지 않은 지역 코드가 하나라도 있으면 지역 빈 값, 본인 소득인데 금액·기간이 없거나 범위(월 30만~3,000만원) 밖이면 UNKNOWN
  - `PolicyConditionExtractor`: 기존 `AiTextClient` 재사용(기본 모델 등급), 일시적 실패(시간 초과·연결·서버 오류·불완전 응답)만 1회 재시도
  - `PolicyConditionExtractionService`: extractPending(해시·버전이 바뀐 정책만)/extractAll, 병렬 2·호출 사이 200ms(`policy.condition.extraction.parallelism`, `.delay-ms`), 해석 실패는 UNKNOWN으로 저장, 호출 실패는 저장하지 않고 다음 실행 때 재시도, AI를 쓸 수 없으면 중단
  - 관리자 엔드포인트 `POST /api/admin/policy/condition/extract?all=false|true` (X-Admin-Key)
  - `PolicySyncOrchestrator`: 동기화 후 혜택 추출 다음에 조건 추출 자동 실행
  - `Policy.updateFrom`은 condition을 덮지 않음 (혜택과 같음)
3. 추천 필터·정렬 (`21c0b39`)
  - `PolicyConditionMatcher`: 소득은 INDIVIDUAL이고 사용자 구간 **하한** > 상한일 때만 제외 (하한 = 상한이면 통과). 부부합산·중위소득·수급자·조건 없음·UNKNOWN은 제외하지 않음 (policy-qa-v3와 같은 방침, `IncomeBracket` 재사용)
  - `Policy.matchesRegion` / `matchingRegionCodes`: 기존 region이 있으면 그대로, 비어 있을 때만 추출 지역 사용. 17개 시·도 전체 추출은 전국으로 처리. 응답 regions와 단건 채팅은 기존 `regionCodes()` 그대로
  - `ApplyPeriodClassifier`: 신청기간 구분 코드 → 온통청년 "YYYYMMDD ~ YYYYMMDD"(여러 기간) → "~" 앞뒤 날짜(KINFA 형식) → "상시·연중·수시·소진" 순으로 판정. 사업 기간은 쓰지 않음
  - `PolicyRecommendationOrder`: 신청 가능 여부 → 사용자 지역 → 조건 UNKNOWN 수, 안정 정렬
  - `PolicyService.findCandidatePolicies`에 필터, `getMatchingPolicies`에만 정렬 적용
4. 프롬프트 보강·문서
  - 샘플 대조 후 규칙 추가: 선택 조건 중 하나인 소득·예외가 있는 소득은 UNKNOWN, 정책명의 시·군·구와 "○○구민"·"관내"를 지역 근거로 봄
  - Swagger·docs/API_Spec.md 정책 목록 설명에 필터·정렬 규칙 추가 (응답 형식 변경 없음 명시)
  - docs/ai/prompt-changelog.md에 조건 추출 프롬프트 v1 추가
  - docs/policy-condition-sample.md: 로컬 추출 결과, 샘플 38건 대조, 추천 전후 비교, 운영 확인 SQL

### 🧪 테스트 방법 (How to Test)
- 단위 테스트: `./gradlew test` → 1,000건 통과 (기존 skip 8건), 신규 73건
  - 파서: 정상 JSON(연→월 환산), 숫자 문자열, 정의되지 않은 enum·지역 코드, 누락 필드, 범위 밖 금액, 코드 블록, 파싱 실패
  - 추출 서비스: 해시·버전이 같으면 건너뜀, 바뀌면 재추출, all, 해석 실패 → UNKNOWN 저장, 호출 실패 → 미저장 후 계속, AI 불가 → 중단, 추출 중 삭제된 정책
  - 추출기: 일시적 실패 1회 재시도, 재시도 대상이 아닌 실패, 원문 없음 → AI 미호출
  - 매칭: 소득 경계(하한 = 상한 통과, 초과 제외), 사용자 소득 null, 본인 소득 외 기준 통과, 주택 4조합 + null, 대상 BUSINESS/BOTH/INDIVIDUAL × 직업 유형, 지역 보완(일치·불일치·기존 region 우선·17개 전체), 기존 지역·나이 필터와 함께 동작
  - 정렬: 신청 가능 > 판정 불가 > 마감·시작 전, 지역 정책 > 전국, 조건 확인 순, 동순위 기존 순서
  - 저장: 조건·신청기간 코드 컬럼 저장/조회, 동기화(updateFrom) 후 조건 유지·원문 변경 시 재추출 대상
  - 기존 테스트 1건 수정: `PolicyServiceTest.사용자_지역과_일치하는_정책과_전국_정책만_반환한다`의 결과 순서 (사용자 지역 정책이 먼저 오도록 바뀐 의도된 변화)
- 로컬 실호출: 정책 864건 전체 추출 2회 (해석 실패·호출 실패 0건), 테스트 사용자 7명의 추천 전후 비교 → docs/policy-condition-sample.md
- 배포 후: 아래 "배포 후 할 일" 순서대로 확인

### 🤔 고려 사항 (Considerations)
- **dev 머지 시 CD로 운영에 바로 배포됨.** 배포 직후에는 모든 정책의 조건이 null(UNKNOWN)이라 필터가 동작하지 않고, 정렬도 신청 기간·지역 기준만 적용됨 (안전한 쪽)
- **배포 후 할 일**
  1. 서버 기동 확인 (`ddl-auto: update`로 policy 테이블에 컬럼 9개 추가: income_basis, income_max_monthly, housing_requirement, target_type, extracted_region, condition_prompt_version, condition_source_hash, condition_extracted_at, apply_period_code)
  2. 일괄 추출: `curl -X POST -H "X-Admin-Key: $ADMIN_API_KEY" "https://<운영>/api/admin/policy/condition/extract"` (응답까지 20분 안팎. 응답을 기다리지 못하면 서버 로그 "정책 조건 추출 완료"로 확인)
  3. (선택) 신청기간 구분 코드를 바로 채우려면 `POST /api/admin/policy/sync` 실행. 안 하면 다음 03:00 동기화 때 채워짐. 그 전까지 온통청년 상시 정책은 정렬에서 "판정 불가"
  4. docs/policy-condition-sample.md의 운영 확인 SQL로 분포·UNKNOWN 비율·지역 보완 건수 확인, 추천 전후 비교
- **03:00 동기화 시 자동 추출**: 동기화 후 혜택 추출 다음에 `extractPending()`이 실행돼 새 정책·원문이 바뀐 정책만 추출함. 원문이 같으면 다시 호출하지 않음
- **예상 호출 수·비용·시간**: 일괄 추출은 정책 수만큼 1회씩(운영 약 900~1,000건), 약 $0.2~0.3 (gpt-6-luna 입력 $0.10·출력 $0.50 / 100만 토큰), 17~20분. 이후 동기화 때는 바뀐 정책만 호출
- **추출 정확도 한계** (로컬 864건 기준)
  - UNKNOWN 비율: 소득 14.0%, 주택 0.9%, 대상 4.1%. 세 조건 모두 UNKNOWN 2건
  - 샘플 38건 중 1차에서 잘못 제외될 수 있던 사례(선택 조건 중 하나인 소득, 예외가 있는 소득)는 프롬프트 보강 후 UNKNOWN으로 바뀜
  - 남은 경계 사례: "어업 독립경영자"를 사업자 전용으로 봄, 사업자 전용 정책의 근로자 급여 기준을 본인 소득 상한으로 읽음
  - 사업자 전용 204건, 무주택 필수 83건 전체를 사람이 검수하지는 않음
  - 기존 region이 있는 정책에도 extracted_region을 채우는 경우가 많음 (코드에서 무시하므로 결과 영향 없음)
- **통합 정책 답변(#183)·정책 검색(#182) 영향**: 두 기능 모두 `findCandidatePolicies`를 쓰므로 새 필터가 후보 범위에도 적용됨 (확실히 해당하지 않는 정책만 빠짐). 정렬은 `getMatchingPolicies`에만 적용해 두 기능에는 영향 없음. 단건 채팅(policy-qa-v3) 프롬프트와 동작은 바꾸지 않음 (채팅의 "신청 가능 지역"은 기존 region 그대로)
- **정렬 규칙과 신청 기간 판정 불가 건수** (로컬, 신청기간 코드 채운 뒤): 판정 불가 86건(10%) — 온통청년 12(코드·기간 모두 없음), KINFA 74("기관 문의" 등 텍스트 53, 빈 값 21). 코드를 채우기 전(배포 직후)은 온통청년 빈 신청 기간 405건도 판정 불가
- **추천 결과 변화** (로컬 테스트 사용자): 직장인·학생·구직자는 사업자 전용 정책이 빠져 15~25% 감소, 유주택자는 무주택 필수 정책까지 빠져 최대 35% 감소, 자영업자는 4~5% 감소
- **프론트 수정 없음**: 목록 건수와 순서만 바뀜. 새 컬럼은 응답에 넣지 않음
- 기존 `Policy.incomeLimit`은 쓰지 않음: `findMatchingPolicies`가 사용자 소득 대표값과 비교하므로, 채우면 "구간 하한 기준으로 확실히 초과할 때만 제외" 원칙이 깨짐
- 정렬의 "기존 순서"는 `findMatchingPolicies` 조회 순서(ORDER BY 없음)라 DB 상태에 따라 동순위 안의 순서가 달라질 수 있음
- 온보딩·User·프로필 API는 수정하지 않음
