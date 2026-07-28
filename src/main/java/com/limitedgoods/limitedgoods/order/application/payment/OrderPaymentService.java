package com.limitedgoods.limitedgoods.order.application.payment;

import com.limitedgoods.limitedgoods.cart.service.CartService;
import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.event.outbox.entity.OutboxEventType;
import com.limitedgoods.limitedgoods.event.outbox.service.OutboxEventWriter;
import com.limitedgoods.limitedgoods.event.payload.order.OrderPaidEvent;
import com.limitedgoods.limitedgoods.event.payload.order.OrderPaidItem;
import com.limitedgoods.limitedgoods.order.application.history.OrderStatusHistoryService;
import com.limitedgoods.limitedgoods.order.application.mapper.OrderResponseMapper;
import com.limitedgoods.limitedgoods.order.application.support.OrderAccessService;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.repository.OrderItemRepository;
import com.limitedgoods.limitedgoods.payment.metrics.PaymentMetricEvent;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final ProductRepository productRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final OrderItemRepository orderItemRepository;
    private final OrderResponseMapper orderResponseMapper;
    private final CartService cartService;
    private final OrderStatusHistoryService historyService;
    private final OrderAccessService orderAccessService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResponse finalizeApprovedPayment(Long userId, Long orderId) {

        Order order = orderAccessService.getOwnedOrderForUpdate(orderId, userId);

        if (order.getStatus() == OrderStatus.PAID) {
            return orderResponseMapper.toResponse(order);
        }

        if (order.getStatus() != OrderStatus.PAYMENT_APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "현재 주문 상태 = " + order.getStatus());
        }

        List<OrderItem> orderItemList =
                orderItemRepository.findByOrderId(orderId);
        if (orderItemList.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        OrderStatus previousStatus = order.getStatus();

        order.markPaid();

        //product의 soldOut 재고 갯수 업데이트
        updateProductSoldCount(orderItemList);

        List<Long> orderedProductIdList = orderItemList.stream()
                .map(item -> item.getProduct().getId())
                .distinct()
                .toList();

        cartService.removeOrderedItemList(userId, orderedProductIdList);

        historyService.record(
                order,
                previousStatus,
                OrderStatus.PAID,
                "결제 내부 확정 완료",
                order.getUser()
        );

        outboxEventWriter.append(
                OutboxEventType.ORDER_PAID,
                "ORDER",
                order.getId(),
                new OrderPaidEvent(
                        order.getId(),
                        userId,
                        order.getUser().getEmail(),
                        order.getTotalPrice(),
                        order.getCreatedAt(),
                        order.getPaidAt(),
                        orderItemList.stream()
                                .map(item ->
                                        new OrderPaidItem(
                                                item.getProduct().getId(),
                                                item.getQuantity(),
                                                item.getPrice()
                                        )
                                )
                                .toList()
                )
        );

        eventPublisher.publishEvent(
                PaymentMetricEvent.success(order.getTotalPrice()));

        return orderResponseMapper.toResponse(order);
    }

    private void updateProductSoldCount(List<OrderItem> orderItemList) {

        for(OrderItem orderItem : orderItemList) {
            productRepository.increaseSoldCount(
                    orderItem.getProduct().getId(),
                    orderItem.getQuantity());
        }
    }
}
