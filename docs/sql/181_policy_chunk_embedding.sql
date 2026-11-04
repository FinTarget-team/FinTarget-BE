-- #181 정책 청크 임베딩 테이블 (pgvector)
--
-- 이 테이블은 JPA 엔티티가 없어 ddl-auto가 만들지 않는다. 배포 시 이 파일을 직접 실행한다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS).
--
-- 실행 전 조건
--   1. db 이미지가 pgvector/pgvector:pg16 이어야 한다 (docker-compose.yml)
--   2. policy_chunk 테이블이 있어야 한다. #180이 반영된 앱을 한 번 기동하면 ddl-auto가 만든다
--
-- 배포 순서: pg_dump 백업 → db 이미지 교체 → 앱 기동 → 이 파일 실행 → 임베딩 백필 API 호출
--   POST /api/admin/policy/embedding/refresh (X-Admin-Key)
--
-- 실행 예 (서버)
--   docker exec -i fintarget-db psql -U "$DB_USERNAME" -d "$DB_NAME" < docs/sql/181_policy_chunk_embedding.sql

-- 기존 볼륨에는 이미지의 init 스크립트가 실행되지 않으므로 확장을 직접 만든다
CREATE EXTENSION IF NOT EXISTS vector;

-- chunk_id: policy_chunk 1행당 임베딩 1행. 청크가 지워지면(원문 변경으로 재생성, 정책 삭제) 임베딩도 함께 지워진다
-- embedding: text-embedding-3-small, 1536차원 (AiEmbeddingClient.DIMENSIONS와 맞춰야 함)
-- model: 임베딩을 만든 모델명. 설정(openai.embedding-model)과 다르면 다시 임베딩한다
CREATE TABLE IF NOT EXISTS policy_chunk_embedding (
    chunk_id   uuid PRIMARY KEY REFERENCES policy_chunk (chunk_id) ON DELETE CASCADE,
    embedding  vector(1536) NOT NULL,
    model      varchar(100) NOT NULL,
    created_at timestamp    NOT NULL DEFAULT now()
);

-- 벡터 인덱스는 선택 사항이다. 청크 수천 개 규모에서는 전체 비교(순차 스캔)로도 충분히 빠르다.
-- 필요해지면 아래를 실행한다 (코사인 거리 <=> 연산자용).
-- CREATE INDEX IF NOT EXISTS idx_policy_chunk_embedding_hnsw
--     ON policy_chunk_embedding USING hnsw (embedding vector_cosine_ops);
