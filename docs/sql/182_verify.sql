-- #182 정책 검색 서버 확인용 조회 SQL (읽기 전용)
--
-- H2에는 vector 타입도 <=> 연산자도 없어, PolicyChunkSearchRepository의 쿼리는 테스트에서 실행할 수 없다
-- (H2는 <=> 를 "<=" 로 읽고 ANY( 를 기대하며 파싱에 실패한다). 아래 항목이 서버에서 처음 확인되는 부분이다.
-- 묶기·유사도 하한·k 적용은 Java(PolicySearchService)에 있어 PolicySearchServiceTest가 덮는다.
--
-- 실행 예 (서버)
--   docker exec -i fintarget-db psql -U "$DB_USERNAME" -d "$DB_NAME" < docs/sql/182_verify.sql

-- 1. 선행 조건: 청크와 임베딩이 있어야 한다. embedding_count가 0이면 #181 백필을 먼저 돌린다
SELECT (SELECT count(*) FROM policy_chunk)           AS chunk_count,
       (SELECT count(*) FROM policy_chunk_embedding) AS embedding_count;

-- 2. ANY(?) 파라미터 전달 확인
--    리포지토리는 policy_id = ANY(?)에 createArrayOf("uuid", ...)로 배열을 넘긴다.
--    여기서는 정책 2건을 배열 리터럴로 넘겨 같은 형태가 동작하는지 본다.
--    기대: 두 정책의 청크만 나온다 (후보 밖 정책은 섞이지 않는다)
WITH two_policies AS (
    SELECT array_agg(policy_id) AS ids
    FROM (SELECT policy_id FROM policy ORDER BY policy_id LIMIT 2) t
)
SELECT c.policy_id, count(*) AS chunks
FROM policy_chunk c, two_policies
WHERE c.policy_id = ANY(two_policies.ids)
GROUP BY c.policy_id
ORDER BY c.policy_id;

-- 3. <=> 거리 정렬 확인 (리포지토리 쿼리와 같은 형태)
--    임의의 청크 임베딩을 질문 벡터 자리에 넣는다. 실제로는 질문을 임베딩한 벡터가 들어간다.
--    기대: distance 오름차순이고 첫 행이 질문으로 쓴 청크 자신(0.0000)
WITH q AS (
    SELECT embedding FROM policy_chunk_embedding ORDER BY chunk_id LIMIT 1
),
candidates AS (
    SELECT array_agg(policy_id) AS ids FROM policy
)
SELECT c.policy_id, c.chunk_id, c.section_label,
       round((e.embedding <=> q.embedding)::numeric, 4) AS distance
FROM policy_chunk c
JOIN policy_chunk_embedding e ON e.chunk_id = c.chunk_id
CROSS JOIN q
CROSS JOIN candidates
WHERE c.policy_id = ANY(candidates.ids)
ORDER BY distance
LIMIT 15;

-- 4. 같은 정책이 상위를 차지하는 정도 확인 (policy.search.chunk-multiplier 기본값 3의 근거)
--    상위 15개(k=5 × 배수 3) 안에 서로 다른 정책이 몇 건인지 본다.
--    distinct_policies가 5 미만이면 배수를 올려야 한다 (서비스가 WARN 로그를 남긴다)
WITH q AS (
    SELECT embedding FROM policy_chunk_embedding ORDER BY chunk_id LIMIT 1
),
top_chunks AS (
    SELECT c.policy_id
    FROM policy_chunk c
    JOIN policy_chunk_embedding e ON e.chunk_id = c.chunk_id
    CROSS JOIN q
    ORDER BY e.embedding <=> q.embedding
    LIMIT 15
)
SELECT count(*) AS chunks, count(DISTINCT policy_id) AS distinct_policies FROM top_chunks;

-- 5. 유사도 하한값(policy.search.max-distance 기본 0.65) 분포 확인
--    거리 구간별 청크 수. 하한이 과도하게 느슨하거나 조이지 않는지 #184 평가 전에 가늠한다
WITH q AS (
    SELECT embedding FROM policy_chunk_embedding ORDER BY chunk_id LIMIT 1
)
SELECT width_bucket((e.embedding <=> q.embedding)::numeric, 0, 1, 10) AS bucket_0_to_1,
       count(*) AS chunks
FROM policy_chunk_embedding e CROSS JOIN q
GROUP BY bucket_0_to_1
ORDER BY bucket_0_to_1;
