-- =====================================================================
-- 온보딩 완료 사용자 목표(Goal) 백필
-- =====================================================================
-- 목적
--   온보딩 완료 시 Goal 자동 생성 기능 도입 이전에 온보딩을 마친 사용자는
--   Goal이 없다. 이들에게 서버와 동일한 규칙으로 Goal을 한 번 생성한다.
--     - title: '내 목표', current_amount: 0
--     - deadline: 실행일 + 1년, deadline_defaulted: true
--     - target_amount: 가장 최근 온보딩 5단계 답변 (1원 이상 50억 원 이하만)
--
-- 실행 전제
--   - 자동 생성 기능이 배포되어 goal.deadline_defaulted 컬럼이 생성된 상태
--   - PostgreSQL 13 이상 (gen_random_uuid)
--   - 테이블/컬럼명은 엔티티 기준으로 작성했으므로 실행 전 \d users, \d goal,
--     \d onboarding_answer 로 실제 이름을 확인할 것
--
-- 실행 순서: 1 → 2 → 3 (3은 트랜잭션 안에서 결과 확인 후 COMMIT)
-- 이미 Goal이 있는 사용자는 제외하므로 여러 번 실행해도 중복 생성되지 않는다.
--
-- 주의: 아래 쿼리의 step = 5는 이 스크립트를 작성한 시점(#91, 5단계 체계)의 목표 금액
-- 단계 번호다. onboarding_answer.step은 답변을 저장할 때의 번호가 그대로 남고, 이후
-- 단계 번호가 바뀌어도(#87에서 6단계, 다시 7단계 체계로 변경) 기존 행은 변환되지 않는다.
-- 이 스크립트의 대상(Goal 자동 생성 도입 전에 온보딩을 마친 사용자)의 목표 금액 답변은
-- 모두 5단계 체계로 저장되어 있으므로 step = 5를 유지한다. 현재 OnboardingService의
-- STEP_GOAL 값으로 바꾸면 대상 행을 찾지 못해 0건 처리되므로 바꾸지 말 것.
-- (자동 생성 도입 이후 완료한 사용자는 Goal이 이미 생성되어 있어 대상이 아니다.)
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. 대상 건수 확인: 온보딩 완료 + Goal 없음 (0건이면 종료)
-- ---------------------------------------------------------------------
SELECT count(*)
FROM users u
LEFT JOIN goal g ON g.user_id = u.id::uuid
WHERE u.onboarding_completed = true
  AND g.goal_id IS NULL;


-- ---------------------------------------------------------------------
-- 2. 백필 제외 대상 확인: 최근 5단계 답변이 숫자가 아니거나 범위를 벗어남
--    (과거에는 5단계 값을 검증하지 않고 저장했음)
--    여기 나온 사용자는 Goal 없이 남으며, 프론트에서 GET /api/goals 의
--    data가 null이면 Goal 생성 화면(POST /api/goals)으로 안내한다.
-- ---------------------------------------------------------------------
WITH latest AS (
  SELECT DISTINCT ON (a.user_id) a.user_id, btrim(a.value) AS value
  FROM onboarding_answer a
  WHERE a.step = 5
  ORDER BY a.user_id, a.created_at DESC
)
SELECT l.user_id, l.value
FROM latest l
JOIN users u ON u.id::uuid = l.user_id
LEFT JOIN goal g ON g.user_id = l.user_id
WHERE u.onboarding_completed = true
  AND g.goal_id IS NULL
  -- AND은 평가 순서가 보장되지 않아 숫자가 아닌 값의 캐스팅 오류를 막기 위해 CASE 사용
  AND NOT (CASE WHEN l.value ~ '^\d{1,10}$' THEN l.value::bigint BETWEEN 1 AND 5000000000 ELSE false END);


-- ---------------------------------------------------------------------
-- 3. 백필 실행
--    INSERT 건수 = (1의 건수 - 2의 건수 - 5단계 답변이 아예 없는 사용자) 인지 확인
--    실행 후 1을 다시 돌려 남은 건수가 예상과 같으면 COMMIT, 아니면 ROLLBACK
-- ---------------------------------------------------------------------
BEGIN;

WITH latest AS (
  SELECT DISTINCT ON (a.user_id) a.user_id, btrim(a.value) AS value
  FROM onboarding_answer a
  WHERE a.step = 5
  ORDER BY a.user_id, a.created_at DESC
)
INSERT INTO goal (goal_id, user_id, title, target_amount, current_amount,
                  deadline, deadline_defaulted, status, created_at, updated_at)
SELECT gen_random_uuid(), l.user_id, '내 목표', l.value::bigint, 0,
       (current_date + interval '1 year')::date, true, 'ACTIVE', now(), now()
FROM latest l
JOIN users u ON u.id::uuid = l.user_id
LEFT JOIN goal g ON g.user_id = l.user_id
WHERE u.onboarding_completed = true
  AND g.goal_id IS NULL
  AND CASE WHEN l.value ~ '^\d{1,10}$' THEN l.value::bigint BETWEEN 1 AND 5000000000 ELSE false END;

-- COMMIT;
-- ROLLBACK;
