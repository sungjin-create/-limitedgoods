package com.limitedgoods.limitedgoods.order.application.create;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.event.outbox.entity.OutboxEventType;
import com.limitedgoods.limitedgoods.event.outbox.service.OutboxEventWriter;
import com.limitedgoods.limitedgoods.event.payload.order.OrderCreatedEvent;
import com.limitedgoods.limitedgoods.event.payload.order.OrderExpiredEvent;
import com.limitedgoods.limitedgoods.order.application.create.dto.OrderStockReservationResult;
import com.limitedgoods.limitedgoods.order.application.history.OrderStatusHistoryService;
import com.limitedgoods.limitedgoods.order.application.mapper.OrderResponseMapper;
import com.limitedgoods.limitedgoods.order.dto.request.OrderItemRequest;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.metrics.OrderCreateMetricEvent;
import com.limitedgoods.limitedgoods.order.metrics.OrderExpiredMetricEvent;
import com.limitedgoods.limitedgoods.order.repository.OrderItemRepository;
import com.limitedgoods.limitedgoods.order.repository.OrderRepository;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.product.service.ProductSoldOutCacheService;
import com.limitedgoods.limitedgoods.queue.service.QueueMaintenanceService;
import com.limitedgoods.limitedgoods.user.entity.User;
import com.limitedgoods.limitedgoods.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class OrderCreateTransactionService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderResponseMapper orderResponseMapper;
    private final ProductSoldOutCacheService productSoldOutCacheService;
    private final OrderItemRepository orderItemRepository;
    private final OrderStockReservationService stockReservationService;
    private final OrderStatusHistoryService historyService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;
    private final QueueMaintenanceService queueMaintenanceService;

    @Transactional
    public OrderResponse createOrder(
            Long userId,
            List<OrderItemRequest> items,
            long reservationSeconds,
            String checkoutToken,
            String requestFingerprint
    ) {
        User user = getUserForUpdate(userId);

        Order existingOrder = findExistingOrder(userId, checkoutToken);

        if (existingOrder != null) {
            validateRequestFingerprint(existingOrder, requestFingerprint);

            return orderResponseMapper.toResponse(existingOrder);
        }

        cancelActivePendingOrder(userId);

        OrderStockReservationResult reservation = stockReservationService.reserve(items);

        registerSoldOutCacheAfterCommit(reservation.productIds());

        Order savedOrder = saveOrder(
                user,
                checkoutToken,
                requestFingerprint,
                reservationSeconds,
                reservation.totalPrice()
        );

        saveOrderItems(savedOrder, reservation.orderItems());

        historyService.createInitialHistory(savedOrder);

        eventPublisher.publishEvent(OrderCreateMetricEvent.success());

        outboxEventWriter.append(
                OutboxEventType.ORDER_CREATED,
                "ORDER",
                savedOrder.getId(),
                new OrderCreatedEvent(
                        savedOrder.getId(),
                        userId,
                        savedOrder.getTotalPrice(),
                        savedOrder.getCreatedAt(),
                        savedOrder.getExpiresAt()
                )
        );

        return orderResponseMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse findIdempotentOrder(Long userId, String checkoutToken, String requestFingerprint) {
        Optional<Order> optionalOrder = findByCheckoutToken(userId, checkoutToken);

        if (optionalOrder.isPresent()) {

            Order order = optionalOrder.get();

            validateRequestFingerprint(order, requestFingerprint);

            return orderResponseMapper.toResponse(order);
        }

        return null;
    }

    private User getUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Order findExistingOrder(Long userId, String checkoutToken) {
        return orderRepository.findByUserIdAndCheckoutToken(userId,checkoutToken)
                .orElse(null);
    }

    private void registerSoldOutCacheAfterCommit(Set<Long> productIds) {
        List<Long> soldOutProductIds = productRepository.findSoldOutProductIds(productIds);

        soldOutProductIds.forEach(productId -> {
            productSoldOutCacheService.markSoldOutAfterCommit(productId);

            queueMaintenanceService.clearProductQueueAfterCommit(productId);
        });
    }

    private Order saveOrder(
            User user,
            String checkoutToken,
            String requestFingerprint,
            long reservationSeconds,
            long totalPrice
    ) {
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(reservationSeconds);

        Order order = Order.create(
                user,
                totalPrice,
                expiresAt,
                checkoutToken,
                requestFingerprint
        );

        return orderRepository.save(order);
    }

    private void saveOrderItems(Order order, List<OrderItem> orderItems) {
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(order);
        }

        orderItemRepository.saveAll(orderItems);
    }

    private void cancelActivePendingOrder(Long userId) {
        List<Order> orderList = orderRepository.findActiveOrdersForUpdate(userId,
                List.of(OrderStatus.CREATED,
                        OrderStatus.PAYMENT_FAILED,
                        OrderStatus.PAYMENT_PENDING,
                        OrderStatus.PAYMENT_APPROVED));

        for (Order order : orderList) {
            if (order.getStatus() == OrderStatus.PAYMENT_PENDING
                    || order.getStatus() == OrderStatus.PAYMENT_APPROVED) {
                throw new BusinessException(ErrorCode.ORDER_STARTING_PAYMENT);
            }

            // CREATED, PAYMENT_FAILED → 직접 처리 (expireOrder 호출 대신)
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

            for (OrderItem item : items) {
                productRepository.increaseStock(
                        item.getProduct().getId(),
                        item.getQuantity()
                );
                productSoldOutCacheService.clearSoldOutAfterCommit(item.getProduct().getId());
            }

            order.markExpired(LocalDateTime.now());

            historyService.record(
                    order,
                    order.getStatus(),
                    OrderStatus.EXPIRED,
                    "새로운 주문 생성으로 이전 주문 만료처리",
                    order.getUser()
            );

            eventPublisher.publishEvent(OrderExpiredMetricEvent.replacedByNewOrder());

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

        }
    }

    private void validateRequestFingerprint(Order order, String requestFingerprint) {
        if (!order.getRequestFingerprint().equals(requestFingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private Optional<Order> findByCheckoutToken(Long userId, String checkoutToken) {
        return orderRepository.findByUserIdAndCheckoutToken(userId,checkoutToken);
    }
}
