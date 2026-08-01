package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.product.infrastructure.redis.ProductRedisKeys;
import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueAvailabilityRedisService {

    private static final Duration SOLD_OUT_TTL = Duration.ofMinutes(10);

    private static final RedisScript<Long> MARK_SOLD_OUT_AND_INVALIDATE_QUEUE_SCRIPT = RedisScript.of(
            new ClassPathResource(
                    "redis/queue/availability/mark-sold-out-and-invalidate-queue.lua"
            ),
            Long.class
    );

    private static final RedisScript<Long> INVALIDATE_IF_SOLD_OUT_SCRIPT = RedisScript.of(
            new ClassPathResource(
                    "redis/queue/availability/invalidate-queue-if-sold-out.lua"
            ),
            Long.class
    );

    private static final RedisScript<Long> INVALIDATE_QUEUE_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/queue/availability/invalidate-queue.lua"),
            Long.class
    );

    private final RedisTemplate<String, String> redisTemplate;

    public void markSoldOutAndInvalidateQueue(Long productId) {
        redisTemplate.execute(
                MARK_SOLD_OUT_AND_INVALIDATE_QUEUE_SCRIPT,
                List.of(
                        ProductRedisKeys.soldOut(productId),
                        QueueRedisKeys.generation(productId),
                        QueueRedisKeys.waiting(productId),
                        QueueRedisKeys.activity(productId)
                ),
                String.valueOf(SOLD_OUT_TTL.toMillis())
        );

        removeActiveProduct(productId);

        log.info("event=product_sold_out_queue_invalidated productId={}", productId);
    }

    public void markSoldOutAndInvalidateAfterCommit(Long productId) {
        executeAfterCommit(() -> markSoldOutAndInvalidateQueue(productId));
    }

    /**
     * @return sold-out 키가 존재하여 실제로 무효화했으면 true
     */
    public boolean invalidateQueueIfSoldOut(Long productId) {
        Long result = redisTemplate.execute(
                INVALIDATE_IF_SOLD_OUT_SCRIPT,
                List.of(
                        ProductRedisKeys.soldOut(productId),
                        QueueRedisKeys.generation(productId),
                        QueueRedisKeys.waiting(productId),
                        QueueRedisKeys.activity(productId)
                )
        );

        boolean invalidated = Long.valueOf(1L).equals(result);

        if (invalidated) {
            removeActiveProduct(productId);

            log.info(
                    "event=sold_out_queue_cleanup productId={}",
                    productId
            );
        }

        return invalidated;
    }

    public void invalidateQueue(Long productId) {
        redisTemplate.execute(
                INVALIDATE_QUEUE_SCRIPT,
                List.of(
                        QueueRedisKeys.generation(productId),
                        QueueRedisKeys.waiting(productId),
                        QueueRedisKeys.activity(productId)
                )
        );

        removeActiveProduct(productId);

        log.info(
                "event=product_queue_invalidated productId={}",
                productId
        );
    }

    private void removeActiveProduct(Long productId) {
        redisTemplate.opsForSet().remove(
                QueueRedisKeys.activeProducts(),
                productId.toString()
        );
    }

    private void executeAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            action.run();
                        } catch (Exception exception) {
                            log.error(
                                    "event=queue_availability_after_commit_failed",
                                    exception
                            );
                        }
                    }
                }
        );
    }
}
