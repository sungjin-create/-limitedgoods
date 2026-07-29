package com.limitedgoods.limitedgoods.order.scheduler;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.payment.service.RefundReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundReconciliationScheduler {

    private final RefundReconciliationService reconciliationService;

    @Scheduled(fixedDelayString ="${payment.refund.reconcile-delay-ms:10000}")
    public void reconcileRefunds() {
        List<Long> attemptIds = reconciliationService.findCandidateIds();

        for (Long attemptId : attemptIds) {
            try {
                reconciliationService.reconcile(attemptId);
            } catch (BusinessException exception) {
                if (exception.getErrorCode()
                        != ErrorCode.PAYMENT_REFUND_PROCESSING
                        && exception.getErrorCode()
                        != ErrorCode.PAYMENT_REFUND_RESULT_UNKNOWN) {
                    log.warn(
                            "환불 reconciliation 실패. attemptId={}",
                            attemptId,
                            exception
                    );
                }
            } catch (Exception exception) {
                log.error(
                        "환불 reconciliation 예외. attemptId={}",
                        attemptId,
                        exception
                );
            }
        }
    }
}