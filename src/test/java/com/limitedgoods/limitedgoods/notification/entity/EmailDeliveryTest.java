package com.limitedgoods.limitedgoods.notification.entity;

import com.limitedgoods.limitedgoods.notification.exception.EmailDeliveryClaimOwnershipLostException;
import com.limitedgoods.limitedgoods.notification.template.EmailTemplateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDeliveryTest {

    @Test
    @DisplayName("이메일을 발송 완료 상태로 변경한다")
    void markSent() {
        EmailDelivery delivery = createDelivery();
        LocalDateTime claimedAt =
                LocalDateTime.of(2026, 7, 24, 10, 0);
        LocalDateTime sentAt = claimedAt.plusSeconds(1);

        UUID claimToken = delivery.markProcessing(claimedAt);

        delivery.markSent(claimToken, sentAt);

        assertThat(delivery.getStatus())
                .isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(delivery.getSentAt()).isEqualTo(sentAt);
        assertThat(delivery.getProcessingStartedAt()).isNull();
        assertThat(delivery.getClaimToken()).isNull();
        assertThat(delivery.getLastError()).isNull();
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 가능한 실패는 FAILED 상태와 백오프 시간을 설정한다")
    void markRetryableFailure() {
        EmailDelivery delivery = createDelivery();
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 24, 10, 0);

        UUID claimToken = delivery.markProcessing(now);

        delivery.markRetryableFailure(
                claimToken,
                "temporary failure",
                5,
                now
        );

        assertThat(delivery.getStatus())
                .isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(delivery.getRetryCount()).isEqualTo(1);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt())
                .isEqualTo(now.plusMinutes(1));
        assertThat(delivery.getProcessingStartedAt()).isNull();
        assertThat(delivery.getClaimToken()).isNull();
        assertThat(delivery.getLastError())
                .isEqualTo("temporary failure");
    }

    @Test
    @DisplayName("최대 시도 횟수에 도달하면 DEAD 상태가 된다")
    void markDeadAfterMaxAttempts() {
        EmailDelivery delivery = createDelivery();
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 24, 10, 0);

        for (int attempt = 0; attempt < 3; attempt++) {
            LocalDateTime attemptedAt = now.plusMinutes(attempt);
            UUID claimToken =
                    delivery.markProcessing(attemptedAt);

            delivery.markRetryableFailure(
                    claimToken,
                    "provider unavailable",
                    3,
                    attemptedAt
            );
        }

        assertThat(delivery.getStatus())
                .isEqualTo(EmailDeliveryStatus.DEAD);
        assertThat(delivery.getRetryCount()).isEqualTo(3);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);

        assertThatThrownBy(
                () -> delivery.markProcessing(now.plusHours(1))
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("영구 실패는 즉시 DEAD 상태로 변경한다")
    void markPermanentFailure() {
        EmailDelivery delivery = createDelivery();
        UUID claimToken = delivery.markProcessing(
                LocalDateTime.of(2026, 7, 24, 10, 0)
        );

        delivery.markPermanentFailure(
                claimToken,
                "invalid recipient"
        );

        assertThat(delivery.getStatus())
                .isEqualTo(EmailDeliveryStatus.DEAD);
        assertThat(delivery.getRetryCount()).isEqualTo(1);
        assertThat(delivery.getClaimToken()).isNull();
        assertThat(delivery.getProcessingStartedAt()).isNull();
        assertThat(delivery.getLastError())
                .isEqualTo("invalid recipient");
    }

    @Test
    @DisplayName("과거 claim token으로 상태를 변경할 수 없다")
    void rejectStaleClaimToken() {
        EmailDelivery delivery = createDelivery();
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 24, 10, 0);

        UUID oldClaimToken = delivery.markProcessing(now);
        UUID currentClaimToken =
                delivery.markProcessing(now.plusMinutes(11));

        assertThat(oldClaimToken).isNotEqualTo(currentClaimToken);
        assertThat(delivery.getLeaseExpiredCount()).isEqualTo(1);

        assertThatThrownBy(
                () -> delivery.markSent(
                        oldClaimToken,
                        now.plusMinutes(12)
                )
        ).isInstanceOf(EmailDeliveryClaimOwnershipLostException.class);

        assertThat(delivery.getStatus())
                .isEqualTo(EmailDeliveryStatus.PROCESSING);
        assertThat(delivery.getClaimToken())
                .isEqualTo(currentClaimToken);
    }

    @Test
    @DisplayName("인프라 장애는 시도 횟수와 재시도 횟수를 소비하지 않는다")
    void releaseAfterInfrastructureFailure() {
        EmailDelivery delivery = createDelivery();
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 24, 10, 0);
        LocalDateTime nextAttemptAt = now.plusMinutes(5);

        UUID claimToken = delivery.markProcessing(now);

        delivery.releaseAfterInfrastructureFailure(
                claimToken,
                "SMTP authentication failed",
                nextAttemptAt
        );

        assertThat(delivery.getStatus())
                .isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(delivery.getAttemptCount()).isZero();
        assertThat(delivery.getRetryCount()).isZero();
        assertThat(delivery.getNextAttemptAt())
                .isEqualTo(nextAttemptAt);
        assertThat(delivery.getProcessingStartedAt()).isNull();
        assertThat(delivery.getClaimToken()).isNull();
        assertThat(delivery.getLastError())
                .isEqualTo("SMTP authentication failed");
    }

    @Test
    @DisplayName("DEAD 이메일을 다시 PENDING 상태로 전환한다")
    void requeueDead() {
        EmailDelivery delivery = createDelivery();
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 24, 10, 0);

        UUID claimToken = delivery.markProcessing(now);
        delivery.markPermanentFailure(
                claimToken,
                "invalid recipient"
        );

        LocalDateTime requeuedAt = now.plusHours(1);
        delivery.requeueDead(requeuedAt);

        assertThat(delivery.getStatus())
                .isEqualTo(EmailDeliveryStatus.PENDING);
        assertThat(delivery.getRetryCount()).isZero();
        assertThat(delivery.getAttemptCount()).isZero();
        assertThat(delivery.getLeaseExpiredCount()).isZero();
        assertThat(delivery.getNextAttemptAt())
                .isEqualTo(requeuedAt);
        assertThat(delivery.getProcessingStartedAt()).isNull();
        assertThat(delivery.getClaimToken()).isNull();
    }

    @Test
    @DisplayName("DEAD 상태가 아닌 이메일은 재등록할 수 없다")
    void cannotRequeueNonDeadDelivery() {
        EmailDelivery delivery = createDelivery();

        assertThatThrownBy(
                () -> delivery.requeueDead(LocalDateTime.now())
        ).isInstanceOf(IllegalStateException.class);
    }

    private EmailDelivery createDelivery() {
        return new EmailDelivery(
                10L,
                20L,
                "buyer@example.com",
                EmailTemplateType.PAYMENT_COMPLETED,
                1
        );
    }
}