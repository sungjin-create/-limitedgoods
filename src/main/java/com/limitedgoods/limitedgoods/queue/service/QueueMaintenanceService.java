package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueMaintenanceService {

    private static final Duration STALE_TIMEOUT =
            Duration.ofSeconds(30);

    private static final int CLEANUP_BATCH_SIZE = 200;

    /*
     * 명시적 대기열 이탈.
     *
     * 반환값:
     * 0: 대기열에 없음
     * 1: 정상 삭제
     * 2: 주문 생성에서 admission token을 사용 중
     */
    private static final RedisScript<Long> LEAVE_SCRIPT = RedisScript.of(
            """
            local trackedToken = redis.call('GET', KEYS[3])
    
            if trackedToken then
                local tokenKey = ARGV[2] .. trackedToken
    
                local tokenValue = redis.call('GET', tokenKey)
    
                if tokenValue
                    and string.sub(tokenValue, 1, 11 ) == 'PROCESSING|' then
                    return 2
                end
    
                redis.call('DEL', tokenKey)
                redis.call('DEL', KEYS[3])
            end
    
            local waitingRemoved = redis.call('ZREM', KEYS[1], ARGV[1])
    
            redis.call('ZREM', KEYS[2], ARGV[1])
    
            return waitingRemoved
            """,
            Long.class
        );

    /*
     * 후보 조회와 실제 삭제 사이에 heartbeat가 들어오는 경쟁 조건을
     * 방지하기 위해 Lua 안에서 activity 점수를 다시 확인한다.
     */
    private static final RedisScript<Long> REMOVE_IF_STALE_SCRIPT = RedisScript.of(
            """
            local lastActivity = redis.call('ZSCORE', KEYS[2], ARGV[1])

            if not lastActivity then
                redis.call('ZREM', KEYS[1], ARGV[1])
                return 1
            end

            if tonumber(lastActivity) > tonumber(ARGV[2]) then
                return 0
            end

            local trackedToken = redis.call('GET', KEYS[3])

            if trackedToken then
                local tokenKey = ARGV[3] .. trackedToken

                local tokenValue = redis.call('GET', tokenKey)

                if tokenValue and string.sub(tokenValue, 1, 11) == 'PROCESSING|' then
                    return 2
                end

                redis.call('DEL', tokenKey)
                redis.call('DEL', KEYS[3])
            end

            redis.call('ZREM', KEYS[1], ARGV[1])

            redis.call('ZREM', KEYS[2], ARGV[1])

            return 1
            """,
            Long.class
        );

    private static final RedisScript<Long> HEARTBEAT_SCRIPT = RedisScript.of(
            """
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])

            if not score then
                return 0
            end

            redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])

            return 1
            """,
            Long.class
        );

    private final RedisTemplate<String, String> redisTemplate;

    public boolean heartbeat(Long userId, Long productId) {
        Long result = redisTemplate.execute(
                HEARTBEAT_SCRIPT,
                List.of(
                        QueueRedisKeys.waiting(productId),
                        QueueRedisKeys.activity(productId)
                ),
                userId.toString(),
                String.valueOf(System.currentTimeMillis())
        );

        return Long.valueOf(1).equals(result);
    }

    public LeaveResult leave(Long userId, Long productId) {
        Long result = redisTemplate.execute(
                LEAVE_SCRIPT,
                List.of(
                        QueueRedisKeys.waiting(productId),
                        QueueRedisKeys.activity(productId),
                        QueueRedisKeys.admissionTrack(productId, userId)
                ),
                userId.toString(),
                QueueRedisKeys.admissionTokenPrefix(productId)
        );

        if (Long.valueOf(2).equals(result)) {
            return LeaveResult.PROCESSING;
        }

        removeActiveProductIfQueueEmpty(productId);

        return Long.valueOf(1).equals(result)
                ? LeaveResult.REMOVED
                : LeaveResult.NOT_FOUND;
    }

    public int removeStaleUsers(Long productId) {
        long cutoff = System.currentTimeMillis() - STALE_TIMEOUT.toMillis();

        Set<String> candidates =
                redisTemplate.opsForZSet().rangeByScore(
                        QueueRedisKeys.activity(productId),
                        0,
                        cutoff,
                        0,
                        CLEANUP_BATCH_SIZE
                );

        if (candidates == null || candidates.isEmpty()) {
            removeActiveProductIfQueueEmpty(productId);
            return 0;
        }

        int removed = 0;

        for (String userId : candidates) {
            Long result = redisTemplate.execute(
                    REMOVE_IF_STALE_SCRIPT,
                    List.of(
                            QueueRedisKeys.waiting(productId),
                            QueueRedisKeys.activity(productId),
                            QueueRedisKeys.admissionTrack(
                                    productId,
                                    Long.valueOf(userId)
                            )
                    ),
                    userId,
                    String.valueOf(cutoff),
                    QueueRedisKeys.admissionTokenPrefix(
                            productId
                    )
            );

            if (Long.valueOf(1).equals(result)) {
                removed++;
            }
        }

        removeActiveProductIfQueueEmpty(productId);
        return removed;
    }

    public Set<String> findActiveProductIds() {
        Set<String> productIds = redisTemplate.opsForSet().members(
                QueueRedisKeys.activeProducts());

        return productIds == null ? Set.of() : productIds;
    }

    /**
     * 판매 종료·품절·비활성화 상품의 대기열을 전체 삭제한다.
     */
    public void clearProductQueue(Long productId) {
        List<String> keys = new ArrayList<>();

        keys.add(QueueRedisKeys.waiting(productId));
        keys.add(QueueRedisKeys.activity(productId));

        keys.addAll(scanKeys(QueueRedisKeys.admissionPattern(productId)));

        redisTemplate.delete(keys);

        redisTemplate.opsForSet().remove(
                QueueRedisKeys.activeProducts(),
                productId.toString()
        );

        log.info(
                "event=product_queue_cleared productId={} deletedKeys={}",
                productId,
                keys.size()
        );
    }

    public void clearProductQueueAfterCommit(
            Long productId
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            clearProductQueue(productId);
            return;
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                clearProductQueue(productId);
                            }
                        }
                );
    }

    private void removeActiveProductIfQueueEmpty(Long productId) {
        Long size = redisTemplate.opsForZSet().size(
                QueueRedisKeys.waiting(productId)
        );

        if (size == null || size == 0) {
            redisTemplate.opsForSet().remove(
                    QueueRedisKeys.activeProducts(),
                    productId.toString()
            );
        }
    }

    /*
     * 운영 Redis에서 KEYS 명령을 사용하면 전체 Redis를 막을 수 있으므로
     * 반드시 SCAN을 사용한다.
     */
    private List<String> scanKeys(String pattern) {
        List<String> keys = redisTemplate.execute(
                (RedisCallback<List<String>>) connection -> {
                    List<String> found = new ArrayList<>();

                    ScanOptions options =
                            ScanOptions.scanOptions()
                                    .match(pattern)
                                    .count(500)
                                    .build();

                    try (Cursor<byte[]> cursor =
                                 connection.scan(options)) {
                        cursor.forEachRemaining(
                                key -> found.add(
                                        new String(key, StandardCharsets.UTF_8)
                                )
                        );
                    }

                    return found;
                }
        );

        return keys == null ? List.of() : keys;
    }

}