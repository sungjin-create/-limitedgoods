package com.limitedgoods.limitedgoods.backoffice.order.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.cancel.CancelOrderUseCase;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.repository.OrderRepository;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttempt;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttemptStatus;
import com.limitedgoods.limitedgoods.payment.repository.RefundAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRefundService {

    private final OrderRepository orderRepository;
    private final RefundAttemptRepository refundAttemptRepository;
    private final CancelOrderUseCase cancelOrderUseCase;

    /**
     * 기존 환불 시도의 PG 결과를 재조회한다.
     *
     * 새로운 환불 시도나 멱등 키를 생성하지 않는다.
     */
    public OrderResponse reconcile(
            Long adminUserId,
            Long orderId
    ) {
        Order order = getOrder(orderId);

        /*
         * 이미 완료된 주문은 CancelOrderUseCase가
         * 현재 주문 응답을 그대로 반환하도록 한다.
         */
        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.info(
                    "event=admin_refund_reconcile_already_completed " +
                            "adminUserId={} orderId={}",
                    adminUserId,
                    orderId
            );

            return cancelOrderUseCase.execute(
                    order.getUser().getId(),
                    orderId
            );
        }

        if (order.getStatus() != OrderStatus.CANCEL_REQUESTED) {
            throw new BusinessException(
                    ErrorCode.REFUND_RECONCILE_NOT_ALLOWED,
                    "현재 주문 상태 = " + order.getStatus()
            );
        }

        RefundAttempt latestAttempt =
                getLatestRefundAttempt(orderId);

        if (latestAttempt.getStatus()
                != RefundAttemptStatus.PROCESSING
                && latestAttempt.getStatus()
                != RefundAttemptStatus.UNKNOWN
                && latestAttempt.getStatus()
                != RefundAttemptStatus.APPROVED) {
            throw new BusinessException(
                    ErrorCode.REFUND_RECONCILE_NOT_ALLOWED,
                    "현재 환불 상태 = " + latestAttempt.getStatus()
            );
        }

        log.info(
                "event=admin_refund_reconcile_requested " +
                        "adminUserId={} orderId={} refundAttemptId={} " +
                        "refundStatus={}",
                adminUserId,
                orderId,
                latestAttempt.getId(),
                latestAttempt.getStatus()
        );

        /*
         * CancelOrderUseCase는 CANCEL_REQUESTED를 만나면
         * 기존 RefundAttempt의 멱등 키로 PG 결과를 조회한다.
         */
        return cancelOrderUseCase.execute(
                order.getUser().getId(),
                orderId
        );
    }

    /**
     * 확정적으로 거절된 환불을 새로운 멱등 키로 다시 요청한다.
     */
    public OrderResponse retry(
            Long adminUserId,
            Long orderId
    ) {
        Order order = getOrder(orderId);

        if (order.getStatus() != OrderStatus.CANCEL_FAILED) {
            throw new BusinessException(
                    ErrorCode.REFUND_RETRY_NOT_ALLOWED,
                    "현재 주문 상태 = " + order.getStatus()
            );
        }

        RefundAttempt latestAttempt =
                getLatestRefundAttempt(orderId);

        /*
         * UNKNOWN 또는 PROCESSING 상태에서 새 키를 발급하면
         * 기존 환불도 성공했을 가능성이 있어 이중 환불 위험이 있다.
         */
        if (latestAttempt.getStatus()
                != RefundAttemptStatus.DECLINED) {
            throw new BusinessException(
                    ErrorCode.REFUND_RETRY_NOT_ALLOWED,
                    "현재 환불 상태 = " + latestAttempt.getStatus()
            );
        }

        log.info(
                "event=admin_refund_retry_requested " +
                        "adminUserId={} orderId={} previousRefundAttemptId={}",
                adminUserId,
                orderId,
                latestAttempt.getId()
        );

        /*
         * CANCEL_FAILED 상태이므로 prepareRefund()가
         * 새로운 RefundAttempt와 멱등 키를 생성한다.
         */
        return cancelOrderUseCase.execute(
                order.getUser().getId(),
                orderId
        );
    }

    private Order getOrder(Long orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));
    }

    private RefundAttempt getLatestRefundAttempt(
            Long orderId
    ) {
        return refundAttemptRepository
                .findTopByOrder_IdOrderByIdDesc(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REFUND_ATTEMPT_NOT_FOUND
                ));
    }
}