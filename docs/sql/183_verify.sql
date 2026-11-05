-- #183 통합 정책 AI 답변 서버 확인용 조회 SQL (읽기 전용)
--
-- #182 검색 확인(docs/sql/182_verify.sql)을 먼저 끝낸 뒤 실행한다. #183은 #182의 검색 쿼리를 그대로 쓴다.
--
-- 실행 예 (서버)
--   docker exec -i fintarget-db psql -U "$DB_USERNAME" -d "$DB_NAME" < docs/sql/183_verify.sql

-- 1. 일일 한도 테이블 생성 확인 (ddl-auto: update로 자동 생성)
--    기대: usage_id, user_id, usage_date, feature, used_count 5개 컬럼과 (user_id, usage_date, feature) 유니크 제약
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'ai_daily_usage'
ORDER BY ordinal_position;

SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'ai_daily_usage'::regclass AND contype = 'u';

-- 2. 프롬프트 입력 크기 가늠 (policy.search.k=5개 청크가 한 프롬프트에 들어간다)
--    출처별 청크 길이(글자 수). p95 × 5가 프롬프트 청크 부분의 대략적인 상한이다.
--    KINFA는 분할하지 않아 길 수 있다. 답변 API 로그의 inputTokens와 함께 본다
SELECT p.source,
       count(*)                                                        AS chunks,
       round(avg(length(c.content)))                                   AS avg_chars,
       percentile_disc(0.95) WITHIN GROUP (ORDER BY length(c.content)) AS p95_chars,
       max(length(c.content))                                          AS max_chars
FROM policy_chunk c
JOIN policy p ON p.policy_id = c.policy_id
GROUP BY p.source
ORDER BY p.source;

-- 3. 오늘(KST) 기능별 사용량. 답변 API를 호출해 본 뒤 used_count가 늘었는지,
--    AI 호출이 실패한 요청 뒤에는 되돌아갔는지 확인한다
SELECT feature,
       count(*)        AS users,
       sum(used_count) AS total_used,
       max(used_count) AS max_used
FROM ai_daily_usage
WHERE usage_date = (now() AT TIME ZONE 'Asia/Seoul')::date
GROUP BY feature
ORDER BY feature;
