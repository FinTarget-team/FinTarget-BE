package kr.fintarget.api.domain.ai.client;

/**
 * @param elapsedMs 첫 시도부터 성공까지 걸린 시간 (재시도 대기 포함)
 * @param attempts  시도 횟수 (재시도가 없었으면 1)
 */
record OpenAiCallResult<T>(T body, long elapsedMs, int attempts) {
}
