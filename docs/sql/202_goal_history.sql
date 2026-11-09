-- #202 목표 재설정 시 새 목표 발급: 운영 DB 제약 변경
--
-- ddl-auto: update는 기존 제약을 지우거나 고치지 않으므로 직접 실행해야 한다.
--   1) goal.user_id 유니크 제약 삭제 (이전 목표가 ARCHIVED로 남아 사용자당 여러 줄이 됨)
--   2) status 허용 값 제약(Hibernate가 enum으로 만든 CHECK)을 ARCHIVED 포함으로 다시 생성
--   3) 진행 중(ACTIVE) 목표는 사용자당 1개만 있도록 부분 유니크 인덱스 생성
--
-- 배포 순서: 이 SQL을 먼저 실행한 뒤 새 버전을 배포한다.
--   이전 버전 코드도 이 상태에서 그대로 동작한다 (사용자당 목표가 아직 1줄이라 findByUserId 결과가 같음).
--   반대로 SQL 없이 새 버전을 먼저 배포하면 목표 금액·마감일 수정이 유니크 제약/CHECK 제약 위반으로 실패한다.
--
-- 실행 예 (서버)
--   docker exec -i fintarget-db psql -U "$DB_USERNAME" -d "$DB_NAME" < docs/sql/202_goal_history.sql

-- 0. 실행 전 확인: goal 테이블의 유니크·CHECK 제약과 status 컬럼 타입
--    기대: user_id 하나만 묶은 유니크 제약 1개, status IN (...) CHECK 제약 1개, status는 character varying
SELECT conname, contype, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'goal'::regclass AND contype IN ('u', 'c')
ORDER BY contype, conname;

SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'goal' AND column_name = 'status';

BEGIN;

-- 1. user_id 하나만 묶은 유니크 제약 삭제 (Hibernate가 만든 이름이 환경마다 달라 이름을 찾아서 지운다)
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'user_id'
        WHERE c.conrelid = 'goal'::regclass
          AND c.contype = 'u'
          AND c.conkey = ARRAY[a.attnum]
    LOOP
        EXECUTE format('ALTER TABLE goal DROP CONSTRAINT %I', r.conname);
        RAISE NOTICE 'dropped unique constraint %', r.conname;
    END LOOP;
END $$;

-- 2. status CHECK 제약을 ARCHIVED 포함으로 다시 생성
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'goal'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE goal DROP CONSTRAINT %I', r.conname);
        RAISE NOTICE 'dropped check constraint %', r.conname;
    END LOOP;
END $$;

ALTER TABLE goal
    ADD CONSTRAINT goal_status_check
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED', 'ARCHIVED'));

-- 3. 진행 중 목표는 사용자당 1개 (기존에는 user_id 유니크라 중복이 있을 수 없음)
CREATE UNIQUE INDEX IF NOT EXISTS uk_goal_user_active
    ON goal (user_id)
    WHERE status = 'ACTIVE';

COMMIT;

-- 4. 실행 후 확인
--    기대: 유니크 제약 없음, goal_status_check에 ARCHIVED 포함, uk_goal_user_active 인덱스가 WHERE status = 'ACTIVE'로 존재
SELECT conname, contype, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'goal'::regclass AND contype IN ('u', 'c')
ORDER BY contype, conname;

SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'goal';

-- 5. 배포 후 확인: 다시 정해 보관된 목표와 그 기록 수 (목표 금액·마감일을 수정한 사용자가 생기면 늘어남)
SELECT g.status, count(DISTINCT g.goal_id) AS goals, count(s.simulation_id) AS simulations
FROM goal g
LEFT JOIN simulation s ON s.goal_id = g.goal_id
GROUP BY g.status
ORDER BY g.status;
