package com.limitedgoods.limitedgoods.order.application.expiration;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.event.outbox.entity.OutboxEventType;
import com.limitedgoods.limitedgoods.event.outbox.service.OutboxEventWriter;
import com.limitedgoods.limitedgoods.event.payload.order.OrderExpiredEvent;
import com.limitedgoods.limitedgoods.order.application.history.OrderStatusHistoryService;
import com.limitedgoods.limitedgoods.order.application.support.OrderAccessService;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.metrics.OrderExpiredMetricEvent;
import com.limitedgoods.limitedgoods.order.purchase.service.UserPurchaseLimitService;
import com.limitedgoods.limitedgoods.order.repository.OrderItemRepository;
import com.limitedgoods.limitedgoods.order.repository.OrderRepository;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.product.service.ProductSoldOutCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductSoldOutCacheService productSoldOutCacheService;
    private final OutboxEventWriter outboxEventWriter;
    private final OrderStatusHistoryService historyService;
    private final OrderAccessService orderAccessService;
    private final ApplicationEventPublisher publisher;
    private final UserPurchaseLimitService userPurchaseLimitService;

    @Transactional
    public void expireOrder(Long orderId) {
        Order order = orderAccessService.getOrderForUpdate(orderId);

        LocalDateTime now = LocalDateTime.now();

        if (order.getStatus() != OrderStatus.CREATED
                && order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            return;
        }

        if (order.getExpiresAt() == null
                || order.getExpiresAt().isAfter(now)) {
            return;
        }

        OrderStatus previousStatus = order.getStatus();

        order.markExpired(now);

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        if (orderItems.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        userPurchaseLimitService.releaseReservation(order.getUser().getId(), orderItems);

        for (OrderItem orderItem : orderItems) {
            Long productId = orderItem.getProduct().getId();

            productRepository.increaseStock(productId, orderItem.getQuantity());
            productSoldOutCacheService.clearSoldOutAfterCommit(productId);
        }


        historyService.record(
                order,
                previousStatus,
                OrderStatus.EXPIRED,
                "결제 기한 만료",
                order.getUser()
        );

        outboxEventWriter.append(
                OutboxEventType.ORDER_EXPIRED,
                "ORDER",
                order.getId(),
                new OrderExpiredEvent(
                        order.getId(),
                        order.getUser().getId(),
                        order.getTotalPrice(),
                        order.getCreatedAt(),
                        LocalDateTime.now()
                )
        );


        publisher.publishEvent(OrderExpiredMetricEvent.timeout());

    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredOrderIds() {
        return orderRepository.findByStatusInAndExpiresAtBefore(
                        List.of(
                                OrderStatus.CREATED,
                                OrderStatus.PAYMENT_FAILED
                        ),
                        LocalDateTime.now()
                )
                .stream()
                .map(Order::getId)
                .toList();
    }
}
