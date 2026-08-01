package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.infrastructure.redis.ProductRedisKeys;
import com.limitedgoods.limitedgoods.queue.dto.QueueAdmissionResult;
import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionTokenService {

    private static final Duration TOKEN_TTL = Duration.ofSeconds(3000000);

    private final RedisTemplate<String, String> redisTemplate;

    private static final RedisScript<String> CLAIM_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/queue/admission/claim-token.lua"),
            String.class
    );

    private static final RedisScript<Long> COMPLETE_CONSUMPTION_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/queue/admission/complete-token-consumption.lua"),
            Long.class
    );

    private static final RedisScript<String> ENTER_QUEUE_AND_ISSUE_TOKEN_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/queue/admission/enter-queue-and-issue-token.lua"),
            String.class
    );

    private static final RedisScript<String> GET_STATUS_AND_ISSUE_TOKEN_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/queue/admission/get-status-and-issue-token.lua"),
            String.class
    );

    private static final RedisScript<Long> RELEASE_CLAIM_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/queue/admission/release-claim.lua"),
            Long.class
    );

    public Long claimToken(String token, Long userId, Long productId, String claimId) {
        String result = redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(
                        QueueRedisKeys.admissionToken(productId, token),
                        QueueRedisKeys.generation(productId)
                ),
                userId.toString(),
                productId.toString(),
                claimId
        );

        return result == null ? null  : Long.valueOf(result);
    }

    public QueueAdmissionResult enterQueueAndIssueToken(Long userId, Long productId, int activeWindow) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String uuid = UUID.randomUUID().toString();

            String result = redisTemplate.execute(
                    ENTER_QUEUE_AND_ISSUE_TOKEN_SCRIPT,
                    List.of(
                            ProductRedisKeys.soldOut(productId),
                            QueueRedisKeys.generation(productId),
                            QueueRedisKeys.waiting(productId),
                            QueueRedisKeys.admissionTrack(productId, userId),
                            QueueRedisKeys.admissionToken(productId, uuid),
                            QueueRedisKeys.activity(productId)
                    ),
                    String.valueOf(System.currentTimeMillis()),
                    userId.toString(),
                    uuid,
                    String.valueOf(TOKEN_TTL.toMillis()),
                    String.valueOf(activeWindow),
                    productId.toString(),
                    QueueRedisKeys.admissionTokenPrefix(productId)
            );

            if (result == null || "ERROR".equals(result)) {
                throw new IllegalStateException("대기열 진입 처리에 실패했습니다.");
            }

            if ("SOLD_OUT".equals(result)) {
                throw new BusinessException(ErrorCode.QUEUE_SOLD_OUT);
            }

            if (result.equals("RETRY")) {
                continue;
            }

            if (result.startsWith("ADMITTED:")) {
                registerActiveProductBestEffort(productId);
                String token = result.substring("ADMITTED:".length());
                return QueueAdmissionResult.admitted(token);
            }

            if (result.startsWith("WAITING:")) {
                registerActiveProductBestEffort(productId);
                int position = Integer.parseInt(result.substring("WAITING:".length()));
                return QueueAdmissionResult.waiting(position);
            }

            throw new IllegalStateException("알 수 없는 대기열 처리 결과입니다: " + result);
        }

        throw new IllegalStateException("입장 토큰 생성에 실패했습니다.");
    }

    public boolean completeTokenConsumption(String token, Long userId, Long productId, String claimId, long claimGeneration) {
        Long result = redisTemplate.execute(
                COMPLETE_CONSUMPTION_SCRIPT,
                List.of(
                        QueueRedisKeys.admissionToken(productId, token),
                        QueueRedisKeys.admissionTrack(productId, userId)
                ),
                String.valueOf(claimGeneration),
                userId.toString(),
                productId.toString(),
                claimId,
                token
        );

        return Long.valueOf(1L).equals(result);
    }

    public boolean releaseClaim(String token, Long userId, Long productId, String claimId, long claimGeneration) {
        Long result = redisTemplate.execute(
                RELEASE_CLAIM_SCRIPT,
                List.of(
                        QueueRedisKeys.admissionToken(productId, token),
                        QueueRedisKeys.admissionTrack(productId, userId),
                        QueueRedisKeys.generation(productId)
                ),
                String.valueOf(claimGeneration),
                userId.toString(),
                productId.toString(),
                claimId,
                token
        );

        return Long.valueOf(1L).equals(result) || Long.valueOf(2L).equals(result);
    }

    public QueueAdmissionResult getStatusAndIssueTokenIfEligible(Long userId, Long productId, int activeWindow) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String token = UUID.randomUUID().toString();

            String result = redisTemplate.execute(
                    GET_STATUS_AND_ISSUE_TOKEN_SCRIPT,
                    List.of(
                            ProductRedisKeys.soldOut(productId),
                            QueueRedisKeys.generation(productId),
                            QueueRedisKeys.waiting(productId),
                            QueueRedisKeys.admissionTrack(productId, userId),
                            QueueRedisKeys.admissionToken(productId, token),
                            QueueRedisKeys.activity(productId)
                    ),
                    String.valueOf(System.currentTimeMillis()),
                    userId.toString(),
                    token,
                    String.valueOf(TOKEN_TTL.toMillis()),
                    String.valueOf(activeWindow),
                    productId.toString(),
                    QueueRedisKeys.admissionTokenPrefix(productId)
            );

            switch (result) {
                case "SOLD_OUT" -> throw new BusinessException(ErrorCode.QUEUE_SOLD_OUT);
                case "NOT_FOUND" -> throw new BusinessException(ErrorCode.QUEUE_NOT_FOUND);
                case "RETRY" -> {
                    continue;
                }
            }

            if (result.startsWith("ADMITTED:")) {
                return QueueAdmissionResult.admitted(result.substring("ADMITTED:".length()));
            }

            if (result.startsWith("WAITING:")) {
                return QueueAdmissionResult.waiting(
                        Integer.parseInt(
                                result.substring("WAITING:".length())
                        )
                );
            }

            throw new IllegalStateException("알 수 없는 대기열 처리 결과: " + result);
        }

        throw new IllegalStateException("입장 토큰 생성에 실패했습니다.");
    }

    private void registerActiveProductBestEffort(Long productId) {
        try {
            redisTemplate.opsForSet().add(QueueRedisKeys.activeProducts(), productId.toString());
        } catch (Exception exception) {
            log.warn("event=active_product_registration_failed productId={}", productId, exception);
        }
    }

}
