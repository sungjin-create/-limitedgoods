package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.product.infrastructure.redis.ProductRedisKeys;
import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final Duration SOLD_OUT_TTL = Duration.ofMinutes(1000);

    /*
     * 1. sold-out 키 등록
     * 2. queue generation 증가
     * 3. waiting/activity 삭제
     *
     * 기존 admission token은 generation 불일치로 즉시 무효화되고,
     * 실제 키는 TTL에 의해 제거된다.
     */
    private static final RedisScript<Long> MARK_SOLD_OUT_AND_INVALIDATE_QUEUE_SCRIPT = RedisScript.of(
        """
        redis.call('SET', KEYS[1], 'true', 'PX', ARGV[1])
    
        local generation = redis.call('INCR', KEYS[2])
    
        redis.call('DEL', KEYS[3])
        redis.call('DEL', KEYS[4])
    
        return generation
        """,
        Long.class
    );

    /*
     * sold-out 키가 현재 존재하는 경우에만 대기열을 무효화한다.
     *
     * 환불이 먼저 sold-out 키를 삭제했다면 아무 작업도 하지 않는다.
     */
    private static final RedisScript<Long> INVALIDATE_IF_SOLD_OUT_SCRIPT = RedisScript.of(
        """
        if redis.call('EXISTS', KEYS[1]) == 0 then
            return 0
        end

        redis.call('INCR', KEYS[2])
        redis.call('DEL', KEYS[3])
        redis.call('DEL', KEYS[4])

        return 1
        """,
        Long.class
    );

    /*
     * 판매 종료, 상품 삭제, 판매 중지 등의 경우에는
     * sold-out 상태와 관계없이 대기열을 무효화한다.
     */
    private static final RedisScript<Long> INVALIDATE_QUEUE_SCRIPT = RedisScript.of(
        """
        local generation = redis.call('INCR', KEYS[1])

        redis.call('DEL', KEYS[2])
        redis.call('DEL', KEYS[3])

        return generation
        """,
        Long.class
    );

    private final RedisTemplate<String, String> redisTemplate;

    public void markSoldOutAndInvalidate(Long productId) {
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
        executeAfterCommit(() -> markSoldOutAndInvalidate(productId));
    }

    /**
     * @return sold-out 키가 존재하여 실제로 무효화했으면 true
     */
    public boolean invalidateIfSoldOut(Long productId) {
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