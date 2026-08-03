package com.limitedgoods.limitedgoods.order.application.payment;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.history.OrderStatusHistoryService;
import com.limitedgoods.limitedgoods.order.application.mapper.OrderResponseMapper;
import com.limitedgoods.limitedgoods.order.application.payment.dto.PaymentStartAction;
import com.limitedgoods.limitedgoods.order.application.payment.dto.PaymentStartResult;
import com.limitedgoods.limitedgoods.order.application.support.OrderAccessService;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.payment.dto.PaymentResult;
import com.limitedgoods.limitedgoods.payment.entity.PaymentAttempt;
import com.limitedgoods.limitedgoods.payment.entity.PaymentAttemptStatus;
import com.limitedgoods.limitedgoods.payment.repository.PaymentAttemptRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private final OrderResponseMapper orderResponseMapper;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderStatusHistoryService historyService;
    private final OrderAccessService orderAccessService;

    @Transactional
    public PaymentStartResult preparePayment(
            Long userId,
            Long orderId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        Order order = orderAccessService.getOwnedOrderForUpdate(orderId, userId);

        if (order.getStatus() == OrderStatus.PAID) {
            return returnPaid(order);
        }

        if (order.getStatus() == OrderStatus.PAYMENT_APPROVED) {
            return finalizeApproved(order);
        }

        PaymentAttempt existingAttempt =
                paymentAttemptRepository
                        .findByOrderIdAndIdempotencyKey(orderId, idempotencyKey)
                        .orElse(null);

        if (existingAttempt != null) {
            validateFingerprint(existingAttempt, requestFingerprint);

            return switch (existingAttempt.getStatus()) {
                case APPROVED -> finalizeApproved(order);
                case PROCESSING, UNKNOWN -> reconcile(existingAttempt);
                case DECLINED -> throw new BusinessException(
                        ErrorCode.PAYMENT_ALREADY_DECLINED
                );
            };
        }

        if (order.getStatus() != OrderStatus.CREATED
                && order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        OrderStatus previousStatus = order.getStatus();
        order.markPaymentPending(LocalDateTime.now());

        PaymentAttempt attempt = paymentAttemptRepository.save(
                PaymentAttempt.create(
                        order,
                        idempotencyKey,
                        requestFingerprint
                )
        );

        historyService.record(
                order,
                previousStatus,
                OrderStatus.PAYMENT_PENDING,
                "결제 요청",
                order.getUser()
        );

        return requestPg(order, attempt);
    }

    @Transactional
    public boolean recordApproval(
            Long userId,
            Long orderId,
            Long paymentAttemptId,
            PaymentResult result
    ) {
        Order order = orderAccessService.getOwnedOrderForUpdate(orderId, userId);
        PaymentAttempt attempt = getAttemptForUpdate(paymentAttemptId);

        validateAttemptOrder(attempt, orderId);

        if (attempt.getStatus() == PaymentAttemptStatus.APPROVED) {
            return true;
        }

        if (result.approvedAmount() != order.getTotalPrice()) {
            attempt.markAmountMismatch(result);
            return false;
        }

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        OrderStatus previousStatus = order.getStatus();

        attempt.approve(result);
        order.markPaymentApproved();

        historyService.record(
                order,
                previousStatus,
                OrderStatus.PAYMENT_APPROVED,
                "PG 승인 완료",
                order.getUser()
        );

        return true;
    }

    @Transactional
    public void recordDecline(
            Long userId,
            Long orderId,
            Long paymentAttemptId,
            String code,
            String reason
    ) {
        Order order = orderAccessService.getOwnedOrderForUpdate(orderId, userId);
        PaymentAttempt attempt = getAttemptForUpdate(paymentAttemptId);

        validateAttemptOrder(attempt, orderId);

        if (attempt.getStatus() == PaymentAttemptStatus.DECLINED) {
            return;
        }

        if (attempt.getStatus() == PaymentAttemptStatus.APPROVED) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS
            );
        }

        attempt.decline(code, reason);

        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            order.markPaymentFailed(reason);

            historyService.record(
                    order,
                    OrderStatus.PAYMENT_PENDING,
                    OrderStatus.PAYMENT_FAILED,
                    reason,
                    order.getUser()
            );

        }

    }

    @Transactional
    public void recordUnknown(
            Long paymentAttemptId,
            String reason
    ) {
        PaymentAttempt attempt = getAttemptForUpdate(paymentAttemptId);

        if (attempt.getStatus() == PaymentAttemptStatus.PROCESSING) {
            attempt.markUnknown(reason);
        }
    }

    private PaymentStartResult returnPaid(Order order) {
        return new PaymentStartResult(
                PaymentStartAction.RETURN_PAID,
                order.getId(),
                order.getTotalPrice(),
                null,
                null,
                orderResponseMapper.toResponse(order)
        );
    }

    private PaymentStartResult finalizeApproved(Order order) {
        return new PaymentStartResult(
                PaymentStartAction.FINALIZE_APPROVED,
                order.getId(),
                order.getTotalPrice(),
                null,
                null,
                null
        );
    }

    private void validateFingerprint(
            PaymentAttempt attempt,
            String requestFingerprint
    ) {
        if (!Objects.equals(
                attempt.getRequestFingerprint(),
                requestFingerprint
        )) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_IDEMPOTENCY_KEY_REUSED
            );
        }
    }

    private PaymentStartResult reconcile(PaymentAttempt attempt) {
        Order order = attempt.getOrder();

        return new PaymentStartResult(
                PaymentStartAction.RECONCILE_PG,
                order.getId(),
                attempt.getAmount(),
                attempt.getId(),
                attempt.getIdempotencyKey(),
                null
        );
    }

    private PaymentStartResult requestPg(Order order, PaymentAttempt attempt) {
        return new PaymentStartResult(
                PaymentStartAction.REQUEST_PG,
                order.getId(),
                order.getTotalPrice(),
                attempt.getId(),
                attempt.getIdempotencyKey(),
                null
        );
    }

    private PaymentAttempt getAttemptForUpdate(
            Long paymentAttemptId
    ) {
        return paymentAttemptRepository
                .findByIdForUpdate(paymentAttemptId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND
                ));
    }

    private void validateAttemptOrder(
            PaymentAttempt attempt,
            Long orderId
    ) {
        if (!Objects.equals(
                attempt.getOrder().getId(),
                orderId
        )) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT
            );
        }
    }

}
