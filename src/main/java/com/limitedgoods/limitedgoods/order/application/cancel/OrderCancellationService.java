package com.limitedgoods.limitedgoods.order.application.cancel;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.cancel.dto.RefundStartAction;
import com.limitedgoods.limitedgoods.order.application.cancel.dto.RefundStartResult;
import com.limitedgoods.limitedgoods.order.application.history.OrderStatusHistoryService;
import com.limitedgoods.limitedgoods.order.application.mapper.OrderResponseMapper;
import com.limitedgoods.limitedgoods.order.application.support.OrderAccessService;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.repository.OrderItemRepository;
import com.limitedgoods.limitedgoods.order.purchase.service.UserPurchaseLimitService;
import com.limitedgoods.limitedgoods.payment.entity.PaymentAttempt;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttempt;
import com.limitedgoods.limitedgoods.payment.repository.PaymentAttemptRepository;
import com.limitedgoods.limitedgoods.payment.repository.RefundAttemptRepository;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.product.service.ProductSoldOutCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderResponseMapper orderResponseMapper;
    private final OrderAccessService orderAccessService;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductSoldOutCacheService productSoldOutCacheService;
    private final OrderStatusHistoryService historyService;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundAttemptRepository refundAttemptRepository;
    private final UserPurchaseLimitService userPurchaseLimitService;

    @Transactional
    public RefundStartResult prepareRefund(Long userId, Long orderId) {
        Order order = orderAccessService.getOwnedOrderForUpdate(orderId, userId);

        if (order.getStatus() == OrderStatus.REFUNDED) {
            return new RefundStartResult(
                    RefundStartAction.RETURN_REFUNDED,
                    null,
                    userId,
                    orderId,
                    null,
                    order.getTotalPrice(),
                    null,
                    orderResponseMapper.toResponse(order)
            );
        }

        if (order.getStatus() == OrderStatus.CANCEL_REQUESTED) {
            RefundAttempt latestAttempt =
                    refundAttemptRepository
                            .findTopByOrder_IdOrderByIdDesc(orderId)
                            .orElseThrow(() ->
                                    new BusinessException(ErrorCode.REFUND_ATTEMPT_NOT_FOUND));

            return switch (latestAttempt.getStatus()) {
                case PROCESSING, UNKNOWN -> throw new BusinessException(
                        ErrorCode.PAYMENT_REFUND_RESULT_UNKNOWN
                );

                case APPROVED ->
                        new RefundStartResult(
                                RefundStartAction.FINALIZE_APPROVED,
                                latestAttempt.getId(),
                                userId,
                                orderId,
                                latestAttempt.getPgTransactionId(),
                                latestAttempt.getAmount(),
                                latestAttempt.getIdempotencyKey(),
                                null
                        );

                case DECLINED -> throw new BusinessException(
                        ErrorCode.PAYMENT_CANCEL_FAILED,
                        "환불이 거절된 상태입니다."
                );
            };
        }

        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(
                    ErrorCode.ORDER_CANCEL_NOT_ALLOWED,
                    "현재 주문 상태 = " + order.getStatus()
            );
        }

        OrderStatus previousStatus = order.getStatus();

        order.requestCancel();

        historyService.record(
                order,
                previousStatus,
                OrderStatus.CANCEL_REQUESTED,
                "사용자 환불 요청",
                order.getUser()
        );

        PaymentAttempt paymentAttempt = getApprovedPaymentAttempt(orderId);

        String idempotencyKey = "refund:" + orderId + ":" + java.util.UUID.randomUUID();

        RefundAttempt refundAttempt = refundAttemptRepository.save(
                RefundAttempt.create(
                        order,
                        paymentAttempt,
                        idempotencyKey,
                        order.getTotalPrice()
                )
        );

        return new RefundStartResult(
                RefundStartAction.REQUEST_PG,
                refundAttempt.getId(),
                userId,
                orderId,
                paymentAttempt.getPgTransactionId(),
                order.getTotalPrice(),
                idempotencyKey,
                null
        );
    }

    @Transactional
    public OrderResponse completeRefund(
            Long userId,
            Long orderId
    ) {
        Order order = orderAccessService.getOwnedOrderForUpdate(orderId, userId);

        if (order.getStatus() == OrderStatus.REFUNDED) {
            return orderResponseMapper.toResponse(order);
        }

        if (order.getStatus() != OrderStatus.CANCEL_REQUESTED) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS,
                    "현재 STATUS = " + order.getStatus()
            );
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

        if (orderItems.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        userPurchaseLimitService.releasePaidQuantity(userId, orderItems);

        for (OrderItem item : orderItems) {
            Long productId = item.getProduct().getId();

            productRepository.increaseStock(productId, item.getQuantity());

            productSoldOutCacheService.clearSoldOutAfterCommit(productId);
        }

        OrderStatus previousStatus = order.getStatus();

        order.markRefunded();

        historyService.record(
                order,
                previousStatus,
                OrderStatus.REFUNDED,
                "PG 환불 완료",
                order.getUser()
        );

        return orderResponseMapper.toResponse(order);
    }

    @Transactional
    public void failRefund(
            Long userId,
            Long orderId,
            String reason
    ) {
        Order order = orderAccessService.getOwnedOrderForUpdate(orderId, userId);

        if (order.getStatus() == OrderStatus.REFUNDED) {
            return;
        }

        if (order.getStatus() != OrderStatus.CANCEL_REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        OrderStatus previousStatus = order.getStatus();

        order.markCancelFailed(reason);

        historyService.record(
                order,
                previousStatus,
                OrderStatus.CANCEL_FAILED,
                reason,
                order.getUser()
        );
    }

    private PaymentAttempt getApprovedPaymentAttempt(Long orderId) {
        return paymentAttemptRepository
                .findApprovedForRefund(orderId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    }

}
