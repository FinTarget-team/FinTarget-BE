-- #224·#225·#227 정책 추천 조건 컬럼: 운영 DB에 policy 컬럼 14개 추가
--
-- 운영 컨테이너는 ddl-auto: validate(#219)라 엔티티에 있는 컬럼이 DB에 없으면 앱이 뜨지 않고 deploy.sh가 이전 버전으로 되돌린다.
-- #224(조건 추출 9개, 신청기간 구분 코드 1개)와 #225(3개)가 이 SQL 없이 머지돼 배포가 롤백됐고, #227(2개)도 같은 컬럼 추가가 필요하다.
--   - 모두 nullable, 기본값 없음. 값은 동기화(신청기간 코드·zipCd 정형값)와 조건 추출(AI)이 채운다
--   - enum 컬럼(income_basis, housing_requirement, target_type, benefit_kind)에는 CHECK 제약을 만들지 않는다.
--     validate는 제약을 검사하지 않고, 제약이 있으면 나중에 enum 값을 추가할 때 저장이 실패한다
--   - IF NOT EXISTS라 다시 실행해도 된다. 앞 버전 코드(#223)는 추가된 컬럼을 쓰지 않으므로 이 상태에서도 그대로 동작한다
--
-- 배포 순서: 이 SQL을 먼저 실행한 뒤 배포한다 (dev 머지 또는 CD 수동 실행).
--   배포 후 POST /api/admin/policy/sync로 신청기간 코드·zipCd 정형값을 채우면 조건 추출이 자동으로 이어진다 (약 20~25분).
--
-- 실행 예 (서버)
--   docker exec -i fintarget-db psql -U "$DB_USERNAME" -d "$DB_NAME" < docs/sql/227_policy_condition_columns.sql

BEGIN;

-- #224 (#220) 신청기간 구분 코드 (PolicyText)
ALTER TABLE policy ADD COLUMN IF NOT EXISTS apply_period_code varchar(10);

-- #224 (#220) 정책 조건 추출 결과 (PolicyCondition)
ALTER TABLE policy ADD COLUMN IF NOT EXISTS income_basis varchar(20);
ALTER TABLE policy ADD COLUMN IF NOT EXISTS income_max_monthly bigint;
ALTER TABLE policy ADD COLUMN IF NOT EXISTS housing_requirement varchar(20);
ALTER TABLE policy ADD COLUMN IF NOT EXISTS target_type varchar(20);
ALTER TABLE policy ADD COLUMN IF NOT EXISTS extracted_region varchar(255);
ALTER TABLE policy ADD COLUMN IF NOT EXISTS condition_prompt_version varchar(40);
ALTER TABLE policy ADD COLUMN IF NOT EXISTS condition_source_hash varchar(64);
ALTER TABLE policy ADD COLUMN IF NOT EXISTS condition_extracted_at timestamp(6) without time zone;

-- #225 경제적 혜택 유형, 시·군·구 한정 여부 (AI 추출값, 온통청년 zipCd 정형값)
ALTER TABLE policy ADD COLUMN IF NOT EXISTS benefit_kind varchar(30);
ALTER TABLE policy ADD COLUMN IF NOT EXISTS local_scope boolean;
ALTER TABLE policy ADD COLUMN IF NOT EXISTS region_local_scope boolean;

-- #227 특정 대상 전용 여부
ALTER TABLE policy ADD COLUMN IF NOT EXISTS special_target boolean;
ALTER TABLE policy ADD COLUMN IF NOT EXISTS special_target_group varchar(50);

COMMIT;

-- 확인: 14행이 나오고 모두 is_nullable = YES, column_default가 비어 있어야 한다
SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'policy'
  AND column_name IN ('apply_period_code', 'income_basis', 'income_max_monthly', 'housing_requirement', 'target_type',
                      'extracted_region', 'condition_prompt_version', 'condition_source_hash', 'condition_extracted_at',
                      'benefit_kind', 'local_scope', 'region_local_scope', 'special_target', 'special_target_group')
ORDER BY column_name;
