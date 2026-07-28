package com.limitedgoods.limitedgoods.order.application.cancel;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.event.outbox.entity.OutboxEventType;
import com.limitedgoods.limitedgoods.event.outbox.service.OutboxEventWriter;
import com.limitedgoods.limitedgoods.event.payload.order.OrderCanceledEvent;
import com.limitedgoods.limitedgoods.event.payload.order.OrderCanceledItem;
import com.limitedgoods.limitedgoods.order.application.cancel.dto.RefundCommand;
import com.limitedgoods.limitedgoods.order.application.history.OrderStatusHistoryService;
import com.limitedgoods.limitedgoods.order.application.mapper.OrderResponseMapper;
import com.limitedgoods.limitedgoods.order.application.support.OrderAccessService;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.repository.OrderItemRepository;
import com.limitedgoods.limitedgoods.payment.entity.PaymentAttempt;
import com.limitedgoods.limitedgoods.payment.repository.PaymentAttemptRepository;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.product.service.ProductSoldOutCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final OutboxEventWriter outboxEventWriter;

    @Transactional
    public RefundCommand prepareRefund(
            Long userId,
            Long orderId
    ) {
        Order order = orderAccessService.getOwnedOrderForUpdate(orderId, userId);

        OrderStatus previousStatus = order.getStatus();

        if (previousStatus == OrderStatus.REFUNDED) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELED);
        }

        if (previousStatus == OrderStatus.CANCEL_REQUESTED) {
            throw new BusinessException(ErrorCode.PAYMENT_REFUND_PROCESSING);
        }

        if (previousStatus == OrderStatus.PAID) {
            order.requestCancel();

            historyService.record(
                    order,
                    previousStatus,
                    OrderStatus.CANCEL_REQUESTED,
                    "사용자 환불 요청",
                    order.getUser()
            );

        } else if (previousStatus == OrderStatus.CANCEL_FAILED) {
            order.retryCancel();

            historyService.record(
                    order,
                    previousStatus,
                    OrderStatus.CANCEL_REQUESTED,
                    "사용자 환불 재시도",
                    order.getUser()
            );

        } else {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED,
                    "현재 주문 STATUS = " + previousStatus);
        }

        PaymentAttempt paymentAttempt = getApprovedPaymentAttempt(orderId);

        return new RefundCommand(
                userId,
                orderId,
                paymentAttempt.getPgTransactionId(),
                order.getTotalPrice(),
                "refund:" + orderId
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

        outboxEventWriter.append(
                OutboxEventType.ORDER_CANCELED,
                "ORDER",
                orderId,
                new OrderCanceledEvent(
                        orderId,
                        userId,
                        order.getTotalPrice(),
                        order.getCreatedAt(),
                        order.getPaidAt(),
                        order.getRefundedAt(),
                        orderItems.stream()
                                .map(item ->
                                        new OrderCanceledItem(
                                                item.getProduct().getId(),
                                                item.getQuantity(),
                                                item.getPrice()
                                        )
                                )
                                .toList()
                )
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
