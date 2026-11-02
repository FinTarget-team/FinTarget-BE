package kr.fintarget.mockbank.webhook;

/**
 * 한 번의 전송(여러 묶음일 수 있음) 결과 요약.
 *
 * @param errorMessage 실패한 묶음이 있을 때 첫 번째 실패 사유, 없으면 null
 */
public record DeliveryResult(int sent, int duplicate, int failed, String errorMessage) {

    public static final DeliveryResult EMPTY = new DeliveryResult(0, 0, 0, null);

    public int total() {
        return sent + duplicate + failed;
    }

    public boolean hasFailure() {
        return failed > 0;
    }

    public String summary() {
        String summary = String.format("핀타겟 전송 %d건 중 저장 %d건, 중복 %d건", total(), sent, duplicate);
        return failed > 0 ? summary + String.format(", 실패 %d건", failed) : summary;
    }
}
