package com.limitedgoods.limitedgoods.order.application.create;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.create.dto.OrderAdmissionClaim;
import com.limitedgoods.limitedgoods.queue.config.QueueAdmissionProperties;
import com.limitedgoods.limitedgoods.queue.service.AdmissionTokenService;
import com.limitedgoods.limitedgoods.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAdmissionCoordinator {

    private final AdmissionTokenService admissionTokenService;
    private final QueueService queueService;
    private final QueueAdmissionProperties admissionProperties;

    public Optional<OrderAdmissionClaim> claimAdmissionIfRequired(
            String admissionToken,
            Long userId,
            Long admissionRequiredProductId,
            String checkoutToken,
            String requestFingerprint
    ) {
        if (admissionRequiredProductId == null || admissionProperties.isBypassEnabled()) {
            return Optional.empty();
        }

        if (admissionToken == null || admissionToken.isBlank()) {
            throw new BusinessException(ErrorCode.ADMISSION_TOKEN_REQUIRED);
        }

        String claimId = createClaimId(checkoutToken, requestFingerprint);

        Long generation = admissionTokenService.claimToken(admissionToken, userId, admissionRequiredProductId, claimId);

        if (generation == null) {
            throw new BusinessException(ErrorCode.ADMISSION_TOKEN_INVALID);
        }

        return Optional.of(
                new OrderAdmissionClaim(
                        admissionToken,
                        userId,
                        admissionRequiredProductId,
                        claimId,
                        generation
                )
        );
    }

    public void releaseClaimAfterBusinessFailure(Optional<OrderAdmissionClaim> claim) {
        claim.ifPresent(this::releaseClaimBestEffort);
    }

    public void completeClaimAfterOrderCreated(Optional<OrderAdmissionClaim> claim) {
        claim.ifPresent(this::completeClaimBestEffort);
    }

    /**
     * 시스템 오류인 경우 PROCESSING 상태를 유지한다.
     */
    public void retainClaimAfterUnknownFailure(Optional<OrderAdmissionClaim> claim, Throwable throwable) {
        claim.ifPresent(value ->
                log.error(
                        "[주문 생성 결과 불명확] 입장 토큰 선점을 유지합니다. "
                                + "userId={}, productId={}, claimId={}",
                        value.userId(), value.productId(), value.claimId(), throwable)
        );
    }

    private String createClaimId(String checkoutToken, String requestFingerprint) {
        return checkoutToken + ":" + requestFingerprint;
    }

    private void releaseClaimBestEffort(OrderAdmissionClaim claim) {
        try {
            boolean released =
                    admissionTokenService.releaseClaim(
                            claim.admissionToken(),
                            claim.userId(),
                            claim.productId(),
                            claim.claimId(),
                            claim.generation()
                    );

            if (!released) {
                log.warn(
                        "[입장 토큰 선점 해제 실패] userId={}, productId={}",
                        claim.userId(),
                        claim.productId()
                );
            }
        } catch (Exception exception) {
            log.error(
                    "[입장 토큰 선점 해제 오류] userId={}, productId={}",
                    claim.userId(),
                    claim.productId(),
                    exception
            );
        }
    }

    private void completeClaimBestEffort(OrderAdmissionClaim claim) {
        try {
            queueService.removeFromQueue(claim.userId(),claim.productId());
        } catch (Exception exception) {
            log.error(
                    "[주문 성공 후 대기열 제거 실패] userId={}, productId={}",
                    claim.userId(), claim.productId(), exception
            );

            // 현재 적용한 정책:
            // 큐 제거가 실패하면 track 키를 남겨 만료 후 제거를 시도한다.
            return;
        }

        try {
            boolean consumed =
                    admissionTokenService.completeTokenConsumption(
                            claim.admissionToken(),
                            claim.userId(),
                            claim.productId(),
                            claim.claimId(),
                            claim.generation()
                    );

            if (!consumed) {
                log.debug(
                        "[입장 토큰 소비 확정 실패] userId={}, productId={}",
                        claim.userId(), claim.productId());

            }
        } catch (Exception exception) {
            log.error(
                    "[입장 토큰 소비 후처리 실패] userId={}, productId={}",
                    claim.userId(), claim.productId(), exception);
        }
    }

}
