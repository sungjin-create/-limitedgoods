package com.limitedgoods.limitedgoods.queue.scheduler;

import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.queue.service.QueueAvailabilityRedisService;
import com.limitedgoods.limitedgoods.queue.service.QueueMaintenanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueCleanupSchedulerTest {

    @Mock QueueMaintenanceService queueMaintenanceService;
    @Mock ProductRepository productRepository;
    @Mock QueueAvailabilityRedisService queueAvailabilityRedisService;
    @Mock Product product;

    private QueueCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new QueueCleanupScheduler(
                queueMaintenanceService,
                productRepository,
                queueAvailabilityRedisService
        );
    }

    @Test
    void soldOutProductInvalidatesOnlyWhenSoldOutMarkerStillExists() {
        stubActiveLimitedProduct(0);

        scheduler.cleanupQueues();

        verify(queueAvailabilityRedisService).invalidateQueueIfSoldOut(10L);
        verify(queueAvailabilityRedisService, never()).invalidateQueue(10L);
        verify(queueMaintenanceService, never()).removeStaleQueueMembers(10L);
    }

    @Test
    void restoredStockRunsStaleMemberCleanupWithoutInvalidatingQueue() {
        stubActiveLimitedProduct(1);

        scheduler.cleanupQueues();

        verify(queueMaintenanceService).removeStaleQueueMembers(10L);
        verify(queueAvailabilityRedisService, never()).invalidateQueueIfSoldOut(10L);
        verify(queueAvailabilityRedisService, never()).invalidateQueue(10L);
    }

    @Test
    void unavailableProductInvalidatesTheWholeQueue() {
        when(queueMaintenanceService.findActiveProductIds()).thenReturn(Set.of("10"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(product.getType()).thenReturn(ProductType.LIMITED);
        when(product.isPurchasableAt(any(LocalDateTime.class))).thenReturn(false);

        scheduler.cleanupQueues();

        verify(queueAvailabilityRedisService).invalidateQueue(10L);
        verify(queueAvailabilityRedisService, never()).invalidateQueueIfSoldOut(10L);
        verify(queueMaintenanceService, never()).removeStaleQueueMembers(10L);
    }

    @Test
    void malformedActiveProductIdIsIgnored() {
        when(queueMaintenanceService.findActiveProductIds()).thenReturn(Set.of("not-a-number"));

        scheduler.cleanupQueues();

        verify(productRepository, never()).findById(any());
        verify(queueAvailabilityRedisService, never()).invalidateQueue(any());
        verify(queueAvailabilityRedisService, never()).invalidateQueueIfSoldOut(any());
    }

    private void stubActiveLimitedProduct(int stock) {
        when(queueMaintenanceService.findActiveProductIds()).thenReturn(Set.of("10"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(product.getType()).thenReturn(ProductType.LIMITED);
        when(product.isPurchasableAt(any(LocalDateTime.class))).thenReturn(true);
        when(product.getStock()).thenReturn(stock);
    }
}
