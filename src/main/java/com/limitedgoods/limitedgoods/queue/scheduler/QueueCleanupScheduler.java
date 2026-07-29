package com.limitedgoods.limitedgoods.queue.scheduler;

import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.queue.service.QueueMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueCleanupScheduler {

    private final QueueMaintenanceService queueMaintenanceService;
    private final ProductRepository productRepository;

    @Scheduled(fixedDelayString = "${queue.cleanup.delay-ms:10000}")
    public void cleanupQueues() {
        Set<String> activeProductIds = queueMaintenanceService.findActiveProductIds();

        LocalDateTime now = LocalDateTime.now();

        for (String value : activeProductIds) {
            Long productId;

            try {
                productId = Long.valueOf(value);
            } catch (NumberFormatException exception) {
                continue;
            }

            try {
                Product product = productRepository.findById(productId).orElse(null);

                /*
                 * 삭제된 상품, 일반 상품, 품절, 판매 종료,
                 * PAUSED/HIDDEN/ARCHIVED 상태는 전체 정리한다.
                 */
                if (product == null
                        || product.getType() != ProductType.LIMITED
                        || product.getStock() <= 0
                        || !product.isPurchasableAt(now)) {
                    queueMaintenanceService.clearProductQueue(productId);
                    continue;
                }

                int removed = queueMaintenanceService.removeStaleUsers(productId);

                if (removed > 0) {
                    log.info(
                            "event=stale_queue_members_removed " +
                                    "productId={} count={}",
                            productId,
                            removed
                    );
                }

            } catch (Exception exception) {
                log.error(
                        "event=queue_cleanup_failed productId={}",
                        productId,
                        exception
                );
            }
        }
    }
}