package com.limitedgoods.limitedgoods.analytics.handler;

import com.limitedgoods.limitedgoods.analytics.repository.DailyOrderFunnelProjectionRepository;
import com.limitedgoods.limitedgoods.analytics.repository.DailySalesProjectionRepository;
import com.limitedgoods.limitedgoods.analytics.repository.ProductSalesProjectionRepository;
import com.limitedgoods.limitedgoods.event.idempotency.entity.ProcessedEvent;
import com.limitedgoods.limitedgoods.event.idempotency.repository.ProcessedEventRepository;
import com.limitedgoods.limitedgoods.event.payload.order.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalAnalyticsEventHandlerTest {

    @Mock DailySalesProjectionRepository dailySalesRepository;
    @Mock ProductSalesProjectionRepository productSalesRepository;
    @Mock DailyOrderFunnelProjectionRepository funnelRepository;
    @Mock ProcessedEventRepository processedEventRepository;

    private InternalAnalyticsEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InternalAnalyticsEventHandler(
                dailySalesRepository,
                productSalesRepository,
                funnelRepository,
                processedEventRepository
        );
    }

    @Test
    void alreadyProcessedEventDoesNotMutateProjections() {
        when(processedEventRepository.existsByEventIdAndConsumerName(1L, "analytics"))
                .thenReturn(true);

        handler.handle(1L, createdEvent());

        verifyNoInteractions(dailySalesRepository, productSalesRepository, funnelRepository);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void paidEventUpdatesDailyProductAndCreationDateFunnel() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 23, 55);
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 28, 0, 5);
        OrderPaidEvent event = new OrderPaidEvent(
                10L, 1L, "buyer@example.com", 40_000L,
                createdAt, paidAt,
                List.of(
                        new OrderPaidItem(100L, 2, 10_000),
                        new OrderPaidItem(200L, 1, 20_000)
                )
        );

        handler.handle(2L, event);

        verify(dailySalesRepository).addPaidOrder(paidAt.toLocalDate(), 40_000L, 3L);
        verify(productSalesRepository).addSale(100L, 2, 20_000L, paidAt);
        verify(productSalesRepository).addSale(200L, 1, 20_000L, paidAt);
        verify(funnelRepository).increment(createdAt.toLocalDate(), 0, 1, 0, 0, 0);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void canceledEventUsesRefundDateAndSubtractableProductQuantities() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        LocalDateTime canceledAt = LocalDateTime.of(2026, 7, 28, 12, 0);
        OrderCanceledEvent event = new OrderCanceledEvent(
                10L, 1L, 30_000L,
                createdAt, createdAt.plusMinutes(1), canceledAt,
                List.of(
                        new OrderCanceledItem(100L, 2, 10_000),
                        new OrderCanceledItem(200L, 1, 10_000)
                )
        );

        handler.handle(3L, event);

        verify(dailySalesRepository).addRefund(canceledAt.toLocalDate(), 30_000L, 3L);
        verify(productSalesRepository).addRefund(100L, 2, 20_000L);
        verify(productSalesRepository).addRefund(200L, 1, 10_000L);
        verify(funnelRepository).increment(createdAt.toLocalDate(), 0, 0, 0, 0, 1);
    }

    @Test
    void lifecycleEventsAreAttributedToOrderCreationDate() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 10, 0);

        handler.handle(4L, new PaymentFailedEvent(
                10L, 1L, createdAt, createdAt.plusMinutes(1), "declined"
        ));
        handler.handle(5L, new OrderExpiredEvent(
                11L, 1L, 10_000L, createdAt, createdAt.plusMinutes(5)
        ));

        InOrder inOrder = inOrder(funnelRepository);
        inOrder.verify(funnelRepository).increment(createdAt.toLocalDate(), 0, 0, 1, 0, 0);
        inOrder.verify(funnelRepository).increment(createdAt.toLocalDate(), 0, 0, 0, 1, 0);
    }

    private OrderCreatedEvent createdEvent() {
        LocalDateTime createdAt = LocalDateTime.now();
        return new OrderCreatedEvent(10L, 1L, 10_000L, createdAt, createdAt.plusMinutes(5));
    }
}
