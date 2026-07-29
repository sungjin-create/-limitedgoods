package com.limitedgoods.limitedgoods.payment.service;

import com.limitedgoods.limitedgoods.order.application.cancel.CancelOrderUseCase;
import com.limitedgoods.limitedgoods.order.application.cancel.dto.RefundOwner;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttempt;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttemptStatus;
import com.limitedgoods.limitedgoods.payment.repository.RefundAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundReconciliationService {

    private final RefundAttemptRepository refundAttemptRepository;
    private final CancelOrderUseCase cancelOrderUseCase;

    @Transactional(readOnly = true)
    public List<Long> findCandidateIds() {
        return refundAttemptRepository.findReconciliationCandidateIds(
                List.of(
                        RefundAttemptStatus.PROCESSING,
                        RefundAttemptStatus.UNKNOWN
                ),
                LocalDateTime.now(),
                Pageable.ofSize(100)
        );
    }

    @Transactional(readOnly = true)
    public RefundOwner findOwner(Long attemptId) {
        RefundAttempt attempt = refundAttemptRepository
                .findById(attemptId)
                .orElseThrow();

        return new RefundOwner(
                attempt.getOrder().getUser().getId(),
                attempt.getOrder().getId()
        );
    }

    public void reconcile(Long attemptId) {
        RefundOwner owner = findOwner(attemptId);

        cancelOrderUseCase.execute(owner.userId(), owner.orderId());
    }

}