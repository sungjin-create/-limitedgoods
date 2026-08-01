package com.limitedgoods.limitedgoods.product.service;

import com.limitedgoods.limitedgoods.product.infrastructure.redis.ProductRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSoldOutCacheService {

    private static final Duration SOLD_OUT_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, String> redisTemplate;

    public boolean isSoldOut(Long productId) {
        try {
            return redisTemplate.hasKey(ProductRedisKeys.soldOut(productId));
        } catch (Exception e) {
            log.warn(
                    "품절 캐시 조회 실패. DB로 요청을 전달합니다. productId={}",
                    productId,
                    e
            );

            return false;
        }
    }

    public void markSoldOut(Long productId) {
        try {
            redisTemplate.opsForValue().set(
                    ProductRedisKeys.soldOut(productId),
                    "true",
                    SOLD_OUT_TTL
            );
        } catch (Exception e) {
            log.warn(
                    "품절 캐시 등록 실패. productId={}",
                    productId,
                    e
            );
        }
    }

    public void markSoldOutAfterCommit(Long productId) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            markSoldOut(productId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        markSoldOut(productId);
                    }
                }
        );
    }

    public void clearSoldOut(Long productId) {
        try {
            redisTemplate.delete(ProductRedisKeys.soldOut(productId));
        } catch (Exception e) {
            log.warn(
                    "품절 캐시 삭제 실패. productId={}",
                    productId,
                    e
            );
        }
    }

    public void clearSoldOutAfterCommit(Long productId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clearSoldOut(productId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        clearSoldOut(productId);
                    }
                }
        );
    }
}
