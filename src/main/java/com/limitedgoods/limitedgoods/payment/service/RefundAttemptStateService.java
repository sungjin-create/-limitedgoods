package com.limitedgoods.limitedgoods.payment.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.payment.dto.RefundResult;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttempt;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttemptStatus;
import com.limitedgoods.limitedgoods.payment.repository.RefundAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundAttemptStateService {

    private final RefundAttemptRepository refundAttemptRepository;

    @Transactional
    public boolean approve(
            Long attemptId,
            RefundResult result
    ) {
        RefundAttempt attempt = getForUpdate(attemptId);

        if (attempt.getStatus() == RefundAttemptStatus.APPROVED) {
            return true;
        }

        attempt.approve(result);

        return attempt.getStatus() == RefundAttemptStatus.APPROVED;
    }

    @Transactional
    public void decline(
            Long attemptId,
            String code,
            String reason
    ) {
        RefundAttempt attempt = getForUpdate(attemptId);

        if (attempt.getStatus() == RefundAttemptStatus.APPROVED) {
            return;
        }

        attempt.decline(code, reason);
    }

    @Transactional
    public void markUnknown(
            Long attemptId,
            String reason
    ) {
        RefundAttempt attempt = getForUpdate(attemptId);

        if (attempt.getStatus() == RefundAttemptStatus.PROCESSING
                || attempt.getStatus() == RefundAttemptStatus.UNKNOWN) {
            attempt.markUnknown(reason);
        }
    }

    private RefundAttempt getForUpdate(Long attemptId) {
        return refundAttemptRepository
                .findByIdForUpdate(attemptId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND
                ));
    }
}