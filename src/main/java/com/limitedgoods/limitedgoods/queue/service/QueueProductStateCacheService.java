package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.infrastructure.redis.ProductRedisKeys;
import com.limitedgoods.limitedgoods.queue.domain.QueueProductState;
import com.limitedgoods.limitedgoods.queue.domain.QueueProductStateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class QueueProductStateCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    public void validateQueueEntryAllowed(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_ID);
        }

        String value;

        try {
            value = redisTemplate.opsForValue()
                    .get(ProductRedisKeys.queueState(productId));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.QUEUE_STATE_UNAVAILABLE);
        }

        // 여기서 DB로 fallback하면 동시 요청이 다시 PostgreSQL로 몰린다.
        if (value == null) {
            throw new BusinessException(ErrorCode.QUEUE_STATE_UNAVAILABLE);
        }

        QueueProductState state;

        try {
            state = QueueProductStateSnapshot.deserialize(value)
                    .stateAt(LocalDateTime.now());
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.QUEUE_STATE_UNAVAILABLE);
        }

        switch (state) {
            case OPEN -> {
                return;
            }
            case CLOSED ->
                    throw new BusinessException(ErrorCode.QUEUE_CLOSED);
            case UNSUPPORTED ->
                    throw new BusinessException(ErrorCode.QUEUE_PRODUCT_NOT_SUPPORTED);
        }
    }

    public void sync(Product product) {
        String snapshot = QueueProductStateSnapshot.from(product).serialize();

        redisTemplate.opsForValue().set(
                ProductRedisKeys.queueState(product.getId()),
                snapshot
        );
    }

    public void syncAfterCommit(Product product) {
        Long productId = product.getId();
        String snapshot = QueueProductStateSnapshot.from(product).serialize();

        executeAfterCommit(() ->
                redisTemplate.opsForValue().set(
                        ProductRedisKeys.queueState(productId),
                        snapshot
                )
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
                        action.run();
                    }
                }
        );
    }
}
