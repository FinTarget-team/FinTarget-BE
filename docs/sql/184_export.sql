-- #184 정책 검색·답변 평가용 스냅샷 내보내기 (읽기 전용)
--
-- 정책 테이블 3개(policy, policy_chunk, policy_chunk_embedding)만 내보낸다. 사용자 테이블(users 등)은 내보내지 않는다.
-- 전체를 READ ONLY 트랜잭션 하나로 실행해, 세 파일이 같은 시점의 데이터가 되고 쓰기가 일어나지 않게 한다.
-- 정책 원문은 매일 03:00 동기화로 바뀌므로 03:00 직후는 피한다 (청크·임베딩 갱신이 끝난 뒤 실행).
--
-- 출력 형식: 파일마다 한 줄에 JSON 객체 1개 (JSON Lines). \copy는 COPY 텍스트 형식으로 쓰므로
-- JSON 안의 역슬래시가 두 번 쓰인다. 로더(PolicySearchSnapshot)가 COPY 텍스트 이스케이프를 풀어서 읽는다.
-- 임베딩은 pgvector 텍스트 형식 "[0.1,0.2,...]" 문자열로 내보낸다.
--
-- 실행 방법 (개인 PC → 서버)
--   1. 서버 접속:        ssh <서버>
--   2. 실행:             docker exec -i fintarget-db psql -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 < docs/sql/184_export.sql
--                        (\copy는 psql이 도는 곳에 파일을 쓴다. docker exec로 실행하면 컨테이너 안 /tmp/fintarget-184-snapshot/)
--   3. 컨테이너에서 꺼내기: docker cp fintarget-db:/tmp/fintarget-184-snapshot ~/fintarget-184-snapshot
--                        docker exec fintarget-db rm -rf /tmp/fintarget-184-snapshot
--   4. 로컬로 가져오기:    scp -r <서버>:~/fintarget-184-snapshot <저장소>/ai-eval-snapshot/<YYYYMMDD>
--                        (ai-eval-snapshot/은 .gitignore 대상. 커밋하지 않는다)
--   5. 서버 정리:         ssh <서버> rm -rf ~/fintarget-184-snapshot
--   6. 평가 실행 시:      AI_EVAL_SNAPSHOT_DIR=<저장소>/ai-eval-snapshot/<YYYYMMDD>
--
-- 결과 파일 (/tmp/fintarget-184-snapshot/)
--   manifest.jsonl   내보낸 시각, 건수, 임베딩 모델 (1줄)
--   policy.jsonl     정책 1건 = 1줄 (원문 컬럼 포함, 약 900줄)
--   chunk.jsonl      청크 1개 = 1줄
--   embedding.jsonl  청크 임베딩 1개 = 1줄 (1536차원, 전체 약 15~20MB)

\set ON_ERROR_STOP on
\! mkdir -p /tmp/fintarget-184-snapshot

BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

\copy (SELECT json_build_object('exportedAt', now(), 'policyCount', (SELECT count(*) FROM policy), 'chunkCount', (SELECT count(*) FROM policy_chunk), 'embeddingCount', (SELECT count(*) FROM policy_chunk_embedding), 'embeddingModels', (SELECT json_agg(DISTINCT model) FROM policy_chunk_embedding))) TO '/tmp/fintarget-184-snapshot/manifest.jsonl'

\copy (SELECT json_build_object('policyId', policy_id, 'source', source, 'externalId', external_id, 'name', name, 'description', description, 'policyType', policy_type, 'region', region, 'minAge', min_age, 'maxAge', max_age, 'incomeLimit', income_limit, 'benefitAmount', benefit_amount, 'supportContent', support_content, 'eligibilityDetail', eligibility_detail, 'exclusionCriteria', exclusion_criteria, 'incomeCondition', income_condition, 'ageCondition', age_condition, 'applyMethod', apply_method, 'screeningMethod', screening_method, 'requiredDocuments', required_documents, 'applyPeriod', apply_period, 'businessPeriod', business_period, 'referenceUrl', reference_url, 'applyUrl', apply_url, 'contactInfo', contact_info, 'etcNote', etc_note, 'benefitType', benefit_type) FROM policy ORDER BY policy_id) TO '/tmp/fintarget-184-snapshot/policy.jsonl'

\copy (SELECT json_build_object('chunkId', chunk_id, 'policyId', policy_id, 'sectionLabel', section_label, 'seq', seq, 'content', content, 'sourceHash', source_hash) FROM policy_chunk ORDER BY policy_id, seq) TO '/tmp/fintarget-184-snapshot/chunk.jsonl'

\copy (SELECT json_build_object('chunkId', chunk_id, 'model', model, 'embedding', embedding::text) FROM policy_chunk_embedding ORDER BY chunk_id) TO '/tmp/fintarget-184-snapshot/embedding.jsonl'

COMMIT;

\! wc -l /tmp/fintarget-184-snapshot/*.jsonl
