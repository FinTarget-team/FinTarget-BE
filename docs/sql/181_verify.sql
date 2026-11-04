-- #181 배포 후 서버 확인용 조회 SQL (읽기 전용)
--
-- 실행 예 (서버)
--   docker exec -i fintarget-db psql -U "$DB_USERNAME" -d "$DB_NAME" < docs/sql/181_verify.sql

-- 1. pgvector 확장 설치 확인: 1행(extversion)이 나와야 한다
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

-- 2. 테이블 생성 확인: embedding 컬럼 타입이 vector 여야 한다
SELECT column_name, udt_name, is_nullable
FROM information_schema.columns
WHERE table_name = 'policy_chunk_embedding'
ORDER BY ordinal_position;

-- 3. 백필 후 임베딩 건수: missing_count가 0이면 모든 청크가 임베딩됐다
SELECT
    (SELECT count(*) FROM policy_chunk)                         AS chunk_count,
    (SELECT count(*) FROM policy_chunk_embedding)               AS embedding_count,
    (SELECT count(*) FROM policy_chunk c
       LEFT JOIN policy_chunk_embedding e ON e.chunk_id = c.chunk_id
      WHERE e.chunk_id IS NULL)                                  AS missing_count;

-- 3-1. 모델별 임베딩 건수 (설정한 모델 하나만 나와야 한다)
SELECT model, count(*) FROM policy_chunk_embedding GROUP BY model;

-- 4. 유사도 조회 1건: 임의의 청크 하나를 질의로 삼아 가장 가까운 청크 5개를 본다.
--    첫 행은 자기 자신(distance 0)이고, 나머지는 비슷한 내용의 정책이어야 한다.
--    distance는 코사인 거리(0에 가까울수록 유사, 1 - 코사인 유사도)
WITH query AS (
    SELECT e.embedding, p.name AS query_policy
    FROM policy_chunk_embedding e
    JOIN policy_chunk c ON c.chunk_id = e.chunk_id
    JOIN policy p ON p.policy_id = c.policy_id
    ORDER BY c.created_at
    LIMIT 1
)
SELECT q.query_policy,
       p.name                                   AS matched_policy,
       c.section_label,
       round((e.embedding <=> q.embedding)::numeric, 4) AS distance
FROM query q
CROSS JOIN policy_chunk_embedding e
JOIN policy_chunk c ON c.chunk_id = e.chunk_id
JOIN policy p ON p.policy_id = c.policy_id
ORDER BY e.embedding <=> q.embedding
LIMIT 5;
