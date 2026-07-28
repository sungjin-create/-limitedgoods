package com.limitedgoods.limitedgoods.analytics.handler;

import com.limitedgoods.limitedgoods.analytics.repository.DailyOrderFunnelProjectionRepository;
import com.limitedgoods.limitedgoods.analytics.repository.DailySalesProjectionRepository;
import com.limitedgoods.limitedgoods.analytics.repository.ProductSalesProjectionRepository;
import com.limitedgoods.limitedgoods.event.idempotency.entity.ProcessedEvent;
import com.limitedgoods.limitedgoods.event.idempotency.repository.ProcessedEventRepository;
import com.limitedgoods.limitedgoods.event.payload.order.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalAnalyticsEventHandler {

    private static final String CONSUMER_NAME =
            "analytics";

    private final DailySalesProjectionRepository
            dailySalesRepository;

    private final ProductSalesProjectionRepository
            productSalesRepository;

    private final DailyOrderFunnelProjectionRepository
            orderFunnelRepository;

    private final ProcessedEventRepository
            processedEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(
            Long eventId,
            OrderCreatedEvent event
    ) {
        if (alreadyProcessed(eventId)) {
            return;
        }

        orderFunnelRepository.increment(
                event.createdAt().toLocalDate(),
                1,
                0,
                0,
                0,
                0
        );

        markProcessed(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(
            Long eventId,
            OrderPaidEvent event
    ) {
        if (alreadyProcessed(eventId)) {
            return;
        }

        long soldQuantity = event.items().stream()
                .mapToLong(OrderPaidItem::quantity)
                .sum();

        dailySalesRepository.addPaidOrder(
                event.paidAt().toLocalDate(),
                event.totalPrice(),
                soldQuantity
        );

        for (OrderPaidItem item : event.items()) {
            long itemRevenue =
                    Math.multiplyExact(
                            (long) item.quantity(),
                            item.unitPrice()
                    );

            productSalesRepository.addSale(
                    item.productId(),
                    item.quantity(),
                    itemRevenue,
                    event.paidAt()
            );
        }

        orderFunnelRepository.increment(
                event.createdAt().toLocalDate(),
                0,
                1,
                0,
                0,
                0
        );

        markProcessed(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(
            Long eventId,
            PaymentFailedEvent event
    ) {
        if (alreadyProcessed(eventId)) {
            return;
        }

        orderFunnelRepository.increment(
                event.createdAt().toLocalDate(),
                0,
                0,
                1,
                0,
                0
        );

        markProcessed(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(
            Long eventId,
            OrderExpiredEvent event
    ) {
        if (alreadyProcessed(eventId)) {
            return;
        }

        orderFunnelRepository.increment(
                event.createdAt().toLocalDate(),
                0,
                0,
                0,
                1,
                0
        );

        markProcessed(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(
            Long eventId,
            OrderCanceledEvent event
    ) {
        if (alreadyProcessed(eventId)) {
            return;
        }

        dailySalesRepository.addRefund(
                event.canceledAt().toLocalDate(),
                event.refundAmount()
        );

        for (OrderCanceledItem item : event.items()) {
            long itemRefundAmount =
                    Math.multiplyExact(
                            (long) item.quantity(),
                            item.unitPrice()
                    );

            productSalesRepository.addRefund(
                    item.productId(),
                    item.quantity(),
                    itemRefundAmount
            );
        }

        orderFunnelRepository.increment(
                event.createdAt().toLocalDate(),
                0,
                0,
                0,
                0,
                1
        );

        markProcessed(eventId);
    }

    private boolean alreadyProcessed(Long eventId) {
        return processedEventRepository
                .existsByEventIdAndConsumerName(
                        eventId,
                        CONSUMER_NAME
                );
    }

    private void markProcessed(Long eventId) {
        processedEventRepository.save(
                new ProcessedEvent(
                        eventId,
                        CONSUMER_NAME
                )
        );
    }
}