package com.limitedgoods.limitedgoods.notification.worker;

import com.limitedgoods.limitedgoods.notification.exception.EmailInfrastructureException;
import com.limitedgoods.limitedgoods.notification.infrastructure.mail.EmailProviderCircuit;
import com.limitedgoods.limitedgoods.notification.service.ClaimedEmailDelivery;
import com.limitedgoods.limitedgoods.notification.service.EmailDeliveryProcessor;
import com.limitedgoods.limitedgoods.notification.service.EmailDeliveryStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Profile("internal-worker")
@ConditionalOnProperty(
        name = "app.mail.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class EmailDeliveryWorker {

    private final EmailDeliveryStateService stateService;
    private final EmailDeliveryProcessor deliveryProcessor;
    private final EmailProviderCircuit providerCircuit;

    @Value("${app.mail.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.mail.processing-lease-seconds:600}")
    private long processingLeaseSeconds;

    @Value("${app.mail.batch-size:10}")
    private int batchSize;

    @Value("${app.mail.infrastructure-backoff-ms:300000}")
    private long infrastructureBackoffMs;

    @Scheduled(fixedDelayString = "${app.mail.retry-delay-ms:5000}")
    public void sendPendingEmails() {
        Instant currentInstant = Instant.now();

        if (providerCircuit.isBlocked(currentInstant)) {
            log.debug("event=email_provider_circuit_open "
                            + "blockedUntil={}",
                    providerCircuit.getBlockedUntil()
            );
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(processingLeaseSeconds);

        List<ClaimedEmailDelivery> claims =
                stateService.claimBatch(
                        now,
                        staleBefore,
                        maxAttempts,
                        batchSize
                );

        processClaims(claims);
    }

    private void processClaims(List<ClaimedEmailDelivery> claims) {
        for (int index = 0; index < claims.size(); index++) {

            ClaimedEmailDelivery claim = claims.get(index);

            try {
                deliveryProcessor.process(claim);

            } catch (EmailInfrastructureException exception) {
                releaseRemainingClaims(claims, index + 1,exception);
                break;

            } catch (Exception exception) {
                log.error(
                        "event=email_delivery_unexpected_failure "
                                + "deliveryId={} errorType={}",
                        claim.deliveryId(),
                        exception.getClass().getSimpleName(),
                        exception
                );
            }
        }
    }

    private void releaseRemainingClaims(
            List<ClaimedEmailDelivery> claims,
            int fromIndex,
            EmailInfrastructureException exception
    ) {
        if (fromIndex >= claims.size()) {
            return;
        }

        Duration backoff = Duration.ofMillis(infrastructureBackoffMs);

        LocalDateTime nextAttemptAt = LocalDateTime.now().plus(backoff);

        List<ClaimedEmailDelivery> remainingClaims = claims.subList(fromIndex, claims.size());

        stateService.releaseBatchAfterInfrastructureFailure(
                remainingClaims,
                "Email provider is unavailable: "
                        + exception.getClass().getSimpleName(),
                nextAttemptAt
        );

        log.error(
                "event=email_provider_unavailable "
                        + "releasedClaims={} retryAt={}",
                remainingClaims.size(),
                nextAttemptAt
        );
    }
}
