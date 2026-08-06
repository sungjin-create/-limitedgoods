package com.limitedgoods.limitedgoods.queue.infrastructure.redis;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.infrastructure.redis.ProductRedisKeys;
import com.limitedgoods.limitedgoods.queue.dto.QueueAdmissionResult;
import com.limitedgoods.limitedgoods.queue.service.AdmissionTokenService;
import com.limitedgoods.limitedgoods.queue.service.LeaveResult;
import com.limitedgoods.limitedgoods.queue.service.QueueAvailabilityRedisService;
import com.limitedgoods.limitedgoods.queue.service.QueueMaintenanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:5173",
        "management.server.port=0",
        "queue.cleanup.delay-ms=3600000",
        "payment.finalize.delay-ms=3600000"
})
class QueueRedisIntegrationTest {

    private static final String JWT_SECRET_BASE64 =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--notify-keyspace-events", "KEA");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("jwt.secret-base64", () -> JWT_SECRET_BASE64);
    }

    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired AdmissionTokenService admissionTokenService;
    @Autowired QueueMaintenanceService queueMaintenanceService;
    @Autowired QueueAvailabilityRedisService queueAvailabilityRedisService;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void queueLuaSupportsLeaveHeartbeatAndStaleUserCorrection() {
        Long productId = 501L;
        Long leavingUserId = 11L;
        Long staleUserId = 12L;

        QueueAdmissionResult leaving = admissionTokenService
                .enterQueueAndIssueToken(leavingUserId, productId, 0);
        assertThat(leaving.admitted()).isFalse();
        assertThat(queueMaintenanceService.heartbeat(leavingUserId, productId)).isTrue();
        assertThat(queueMaintenanceService.leaveQueue(leavingUserId, productId))
                .isEqualTo(LeaveResult.REMOVED);

        QueueAdmissionResult stale = admissionTokenService
                .enterQueueAndIssueToken(staleUserId, productId, 0);
        assertThat(stale.admitted()).isFalse();
        redisTemplate.opsForZSet().add(
                QueueRedisKeys.activity(productId),
                staleUserId.toString(),
                System.currentTimeMillis() - Duration.ofMinutes(1).toMillis()
        );

        assertThat(queueMaintenanceService.removeStaleQueueMembers(productId)).isEqualTo(1);
        assertThat(redisTemplate.opsForZSet().size(QueueRedisKeys.waiting(productId))).isZero();
        assertThat(queueMaintenanceService.findActiveProductIds())
                .doesNotContain(productId.toString());
    }

    @Test
    void admissionClaimIsIdempotentAndCanBeReleasedAndConsumed() {
        Long productId = 502L;
        Long userId = 21L;

        QueueAdmissionResult admission = admissionTokenService
                .enterQueueAndIssueToken(userId, productId, 1);
        String token = admission.admissionToken();

        assertThat(admission.admitted()).isTrue();
        assertThat(admissionTokenService.claimToken(token, userId, productId, "claim-a"))
                .isZero();
        assertThat(admissionTokenService.claimToken(token, userId, productId, "claim-a"))
                .isZero();
        assertThat(admissionTokenService.claimToken(token, userId, productId, "claim-b"))
                .isNull();

        assertThat(admissionTokenService.releaseClaim(
                token, userId, productId, "claim-a", 0L
        )).isTrue();
        assertThat(redisTemplate.opsForValue().get(
                QueueRedisKeys.admissionToken(productId, token)
        )).isEqualTo("READY|0|21|502");

        assertThat(admissionTokenService.claimToken(token, userId, productId, "claim-b"))
                .isZero();
        assertThat(admissionTokenService.completeTokenConsumption(
                token, userId, productId, "claim-b", 0L
        )).isTrue();
        assertThat(redisTemplate.hasKey(QueueRedisKeys.admissionToken(productId, token))).isFalse();
        assertThat(redisTemplate.hasKey(QueueRedisKeys.admissionTrack(productId, userId))).isFalse();
    }

    @Test
    void soldOutInvalidatesQueueAndRestockUsesTheCurrentGeneration() {
        Long productId = 503L;
        Long waitingUserId = 31L;
        Long newUserId = 32L;

        QueueAdmissionResult waiting = admissionTokenService
                .enterQueueAndIssueToken(waitingUserId, productId, 0);
        assertThat(waiting.admitted()).isFalse();
        assertThat(queueMaintenanceService.findActiveProductIds())
                .contains(productId.toString());

        queueAvailabilityRedisService.markSoldOutAndInvalidateQueue(productId);

        assertThat(redisTemplate.opsForValue().get(ProductRedisKeys.soldOut(productId)))
                .isEqualTo("true");
        assertThat(redisTemplate.opsForValue().get(QueueRedisKeys.generation(productId)))
                .isEqualTo("1");
        assertThat(redisTemplate.hasKey(QueueRedisKeys.waiting(productId))).isFalse();
        assertThat(redisTemplate.hasKey(QueueRedisKeys.activity(productId))).isFalse();
        assertThat(queueMaintenanceService.findActiveProductIds())
                .doesNotContain(productId.toString());
        assertThatThrownBy(() -> admissionTokenService
                .enterQueueAndIssueToken(newUserId, productId, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.QUEUE_SOLD_OUT));

        redisTemplate.delete(ProductRedisKeys.soldOut(productId));

        QueueAdmissionResult restored = admissionTokenService
                .enterQueueAndIssueToken(newUserId, productId, 1);
        assertThat(restored.admitted()).isTrue();
        assertThat(redisTemplate.opsForValue().get(
                QueueRedisKeys.admissionToken(productId, restored.admissionToken())
        )).isEqualTo("READY|1|32|503");
        assertThat(queueAvailabilityRedisService.invalidateQueueIfSoldOut(productId)).isFalse();
        assertThat(redisTemplate.opsForZSet().score(
                QueueRedisKeys.waiting(productId), newUserId.toString()
        )).isNotNull();
    }

    @Test
    void completedOrderCanConsumeItsClaimAfterSoldOutChangesGeneration() {
        Long productId = 504L;
        Long userId = 41L;

        QueueAdmissionResult admission = admissionTokenService
                .enterQueueAndIssueToken(userId, productId, 1);
        String token = admission.admissionToken();
        Long claimGeneration = admissionTokenService
                .claimToken(token, userId, productId, "last-stock-order");

        assertThat(claimGeneration).isZero();
        queueAvailabilityRedisService.markSoldOutAndInvalidateQueue(productId);

        assertThat(redisTemplate.opsForValue().get(QueueRedisKeys.generation(productId)))
                .isEqualTo("1");
        assertThat(admissionTokenService.claimToken(
                token, userId, productId, "last-stock-order"
        )).isNull();
        assertThat(admissionTokenService.completeTokenConsumption(
                token, userId, productId, "last-stock-order", claimGeneration
        )).isTrue();
        assertThat(redisTemplate.hasKey(QueueRedisKeys.admissionToken(productId, token))).isFalse();
        assertThat(redisTemplate.hasKey(QueueRedisKeys.admissionTrack(productId, userId))).isFalse();
    }

    @Test
    void releaseAfterGenerationChangeDeletesTheOldClaimInsteadOfRestoringReady() {
        Long productId = 505L;
        Long userId = 51L;

        QueueAdmissionResult admission = admissionTokenService
                .enterQueueAndIssueToken(userId, productId, 1);
        String token = admission.admissionToken();
        Long claimGeneration = admissionTokenService
                .claimToken(token, userId, productId, "failed-order");

        queueAvailabilityRedisService.markSoldOutAndInvalidateQueue(productId);

        assertThat(admissionTokenService.releaseClaim(
                token, userId, productId, "failed-order", claimGeneration
        )).isTrue();
        assertThat(redisTemplate.hasKey(QueueRedisKeys.admissionToken(productId, token))).isFalse();
        assertThat(redisTemplate.hasKey(QueueRedisKeys.admissionTrack(productId, userId))).isFalse();
    }
}
