package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.queue.dto.QueueAdmissionResult;
import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdmissionTokenService {

    private static final Duration TOKEN_TTL = Duration.ofSeconds(300);

    private static final RedisScript<Long> CLAIM_SCRIPT = RedisScript.of(
            """
            local tokenValue = redis.call('GET', KEYS[1])

            if not tokenValue then
                return 0
            end

            if tokenValue == ARGV[2] then
                return 1
            end

            if tokenValue ~= ARGV[1] then
                return 0
            end

            local ttl = redis.call('PTTL', KEYS[1])

            if ttl <= 0 then
                return 0
            end

            redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
            return 1
            """,
            Long.class
    );

    private static final RedisScript<Long> COMPLETE_CONSUMPTION_SCRIPT = RedisScript.of(
            """
            local tokenValue = redis.call('GET', KEYS[1])

            if tokenValue ~= ARGV[1] then
                return 0
            end

            redis.call('DEL', KEYS[1])

            local trackedToken = redis.call('GET', KEYS[2])

            if trackedToken == ARGV[2] then
                redis.call('DEL', KEYS[2])
            end

            return 1
            """,
            Long.class
    );

    private static final RedisScript<Long> RELEASE_CLAIM_SCRIPT = RedisScript.of(
            """
            local tokenValue = redis.call('GET', KEYS[1])

            if tokenValue ~= ARGV[1] then
                return 0
            end

            local ttl = redis.call('PTTL', KEYS[1])

            if ttl <= 0 then
                return 0
            end

            redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
            return 1
            """,
            Long.class
    );
    private static final RedisScript<String>
            ISSUE_TOKEN_SCRIPT = RedisScript.of(
            """
            local existingToken = redis.call(
                'GET',
                KEYS[1]
            )
    
            if existingToken then
                return existingToken
            end
    
            local tokenCreated = redis.call(
                'SET',
                KEYS[2],
                ARGV[1],
                'PX',
                ARGV[3],
                'NX'
            )
    
            if not tokenCreated then
                return nil
            end
    
            redis.call(
                'SET',
                KEYS[1],
                ARGV[2],
                'PX',
                ARGV[3]
            )
    
            return ARGV[2]
            """,
            String.class
    );

    private static final RedisScript<String>
            ENTER_QUEUE_AND_ISSUE_TOKEN_SCRIPT =
            RedisScript.of(
                    """
                    redis.call(
                        'ZADD',
                        KEYS[1],
                        'NX',
                        ARGV[1],
                        ARGV[2]
                    )
    
                    redis.call(
                        'ZADD',
                        KEYS[4],
                        ARGV[1],
                        ARGV[2]
                    )
    
                    local rank = redis.call(
                        'ZRANK',
                        KEYS[1],
                        ARGV[2]
                    )
    
                    if not rank then
                        return 'ERROR'
                    end
    
                    local activeWindow = tonumber(ARGV[6])
    
                    if rank >= activeWindow then
                        local position =
                            rank - activeWindow + 1
    
                        return 'WAITING:' .. position
                    end
    
                    local existingToken =
                        redis.call('GET', KEYS[2])
    
                    if existingToken then
                        return 'ADMITTED:' .. existingToken
                    end
    
                    local tokenCreated = redis.call(
                        'SET',
                        KEYS[3],
                        ARGV[3],
                        'PX',
                        ARGV[5],
                        'NX'
                    )
    
                    if not tokenCreated then
                        return 'RETRY'
                    end
    
                    redis.call(
                        'SET',
                        KEYS[2],
                        ARGV[4],
                        'PX',
                        ARGV[5]
                    )
    
                    return 'ADMITTED:' .. ARGV[4]
                    """,
                    String.class
            );
    private static final RedisScript<String>
            GET_STATUS_AND_ISSUE_TOKEN_SCRIPT =
            RedisScript.of(
                    """
                    local rank = redis.call(
                        'ZRANK',
                        KEYS[1],
                        ARGV[2]
                    )
    
                    if not rank then
                        return 'NOT_FOUND'
                    end
    
                    redis.call(
                        'ZADD',
                        KEYS[4],
                        ARGV[1],
                        ARGV[2]
                    )
    
                    local activeWindow = tonumber(ARGV[6])
    
                    if rank >= activeWindow then
                        local position = rank - activeWindow + 1
                        return 'WAITING:' .. position
                    end
    
                    local existingToken = redis.call(
                        'GET',
                        KEYS[2]
                    )
    
                    if existingToken then
                        return 'ADMITTED:' .. existingToken
                    end
    
                    local tokenCreated = redis.call(
                        'SET',
                        KEYS[3],
                        ARGV[3],
                        'PX',
                        ARGV[5],
                        'NX'
                    )
    
                    if not tokenCreated then
                        return 'RETRY'
                    end
    
                    redis.call(
                        'SET',
                        KEYS[2],
                        ARGV[4],
                        'PX',
                        ARGV[5]
                    )
    
                    return 'ADMITTED:' .. ARGV[4]
                    """,
                    String.class
            );

    private final RedisTemplate<String, String> redisTemplate;

    public boolean claim(String token, Long userId, Long productId, String claimId) {
        Long result = redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(QueueRedisKeys.admissionToken(productId, token)),
                tokenValue(userId, productId),
                processingValue(
                        userId,
                        productId,
                        claimId
                )
        );

        return Long.valueOf(1L).equals(result);
    }

    public boolean completeConsumption(String token, Long userId, Long productId, String claimId) {
        Long result = redisTemplate.execute(
                COMPLETE_CONSUMPTION_SCRIPT,
                List.of(
                        QueueRedisKeys.admissionToken(productId, token),
                        QueueRedisKeys.admissionTrack(productId, userId)
                ),
                processingValue(
                        userId,
                        productId,
                        claimId
                ),
                token
        );

        return Long.valueOf(1L).equals(result);
    }

    public boolean releaseClaim(String token, Long userId, Long productId, String claimId) {
        Long result = redisTemplate.execute(
                RELEASE_CLAIM_SCRIPT,
                List.of(QueueRedisKeys.admissionToken(productId, token)),
                processingValue(
                        userId,
                        productId,
                        claimId
                ),
                tokenValue(userId, productId)
        );

        return Long.valueOf(1L).equals(result);
    }

    public QueueAdmissionResult enterQueueAndIssueToken(
            Long userId,
            Long productId,
            int activeWindow
    ) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String uuid = UUID.randomUUID().toString();

            String result = redisTemplate.execute(
                    ENTER_QUEUE_AND_ISSUE_TOKEN_SCRIPT,
                    List.of(
                            QueueRedisKeys.waiting(productId),
                            QueueRedisKeys.admissionTrack(productId, userId),
                            QueueRedisKeys.admissionToken(productId, uuid),
                            QueueRedisKeys.activity(productId)
                    ),
                    String.valueOf(System.currentTimeMillis()),
                    userId.toString(),
                    tokenValue(userId, productId),
                    uuid,
                    String.valueOf(TOKEN_TTL.toMillis()),
                    String.valueOf(activeWindow)
            );

            if (result == null || result.equals("ERROR")) {
                throw new IllegalStateException("대기열 입장 처리에 실패했습니다.");
            }

            if (result.equals("RETRY")) {
                continue;
            }

            if (result.startsWith("ADMITTED:")) {
                String token = result.substring("ADMITTED:".length());
                return QueueAdmissionResult.admitted(token);
            }

            if (result.startsWith("WAITING:")) {
                int position = Integer.parseInt(result.substring("WAITING:".length()));
                return QueueAdmissionResult.waiting(position);
            }

            throw new IllegalStateException("알 수 없는 대기열 처리 결과입니다: " + result);
        }

        throw new IllegalStateException("입장 토큰 생성에 실패했습니다.");
    }

    public QueueAdmissionResult getStatusAndIssueTokenIfEligible(
            Long userId,
            Long productId,
            int activeWindow
    ) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String uuid = UUID.randomUUID().toString();

            String result = redisTemplate.execute(
                    GET_STATUS_AND_ISSUE_TOKEN_SCRIPT,
                    List.of(
                            QueueRedisKeys.waiting(productId),
                            QueueRedisKeys.admissionTrack(productId, userId),
                            QueueRedisKeys.admissionToken(productId, uuid),
                            QueueRedisKeys.activity(productId)
                    ),
                    String.valueOf(System.currentTimeMillis()),
                    userId.toString(),
                    tokenValue(userId, productId),
                    uuid,
                    String.valueOf(TOKEN_TTL.toMillis()),
                    String.valueOf(activeWindow)
            );

            if (result == null) {
                throw new IllegalStateException("대기열 상태 조회에 실패했습니다.");
            }

            if ("NOT_FOUND".equals(result)) {
                throw new BusinessException(ErrorCode.QUEUE_NOT_FOUND);
            }

            if ("RETRY".equals(result)) {
                continue;
            }

            if (result.startsWith("ADMITTED:")) {
                return QueueAdmissionResult.admitted(
                        result.substring("ADMITTED:".length())
                );
            }

            if (result.startsWith("WAITING:")) {
                return QueueAdmissionResult.waiting(
                        Integer.parseInt(
                                result.substring("WAITING:".length())
                        )
                );
            }

            throw new IllegalStateException(
                    "알 수 없는 대기열 처리 결과입니다: " + result
            );
        }

        throw new IllegalStateException("입장 토큰 생성에 실패했습니다.");
    }

    private String tokenValue(Long userId, Long productId) {
        return userId + ":" + productId;
    }

    private String processingValue(Long userId, Long productId, String checkoutToken) {
        return "PROCESSING:" + userId + ":" + productId + ":" + checkoutToken;
    }

}
