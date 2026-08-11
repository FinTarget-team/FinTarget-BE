package kr.fintarget.api.domain.policy.service;

public record PolicySyncResult(
        int youthCenterSavedCount,
        boolean youthCenterFailed,
        int kinfaSavedCount,
        boolean kinfaFailed,
        long elapsedMs
) {
}
