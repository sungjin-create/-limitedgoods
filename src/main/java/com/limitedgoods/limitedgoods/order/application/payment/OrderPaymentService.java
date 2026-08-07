package com.limitedgoods.limitedgoods.order.application.payment;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.history.OrderStatusHistoryService;
import com.limitedgoods.limitedgoods.order.application.mapper.OrderResponseMapper;
import com.limitedgoods.limitedgoods.order.application.support.OrderAccessService;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.purchase.service.UserPurchaseLimitService;
import com.limitedgoods.limitedgoods.order.repository.OrderItemRepository;
import com.limitedgoods.limitedgoods.order.event.OrderPaidPayload;
import com.limitedgoods.limitedgoods.messaging.outbox.application.OutboxWriter;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderResponseMapper orderResponseMapper;
    private final OrderStatusHistoryService historyService;
    private final OrderAccessService orderAccessService;
    private final UserPurchaseLimitService userPurchaseLimitService;
    private final OutboxWriter outboxWriter;

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

        userPurchaseLimitService.confirmPayment(userId, orderItemList);

        order.markPaid();

        //product의 soldOut 재고 갯수 업데이트
        updateProductSoldCount(orderItemList);

        historyService.record(
                order,
                previousStatus,
                OrderStatus.PAID,
                "결제 내부 확정 완료",
                order.getUser()
        );

        outboxWriter.append(
                "orders.lifecycle.v1",
                order.getId().toString(),
                "Order",
                order.getId().toString(),
                "order.paid.v1",
                1,
                new OrderPaidPayload(
                        order.getId(),
                        order.getUser().getId(),
                        order.getTotalPrice(),
                        order.getPaidAt()
                )
        );

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
