package com.limitedgoods.limitedgoods.notification.service;

import com.limitedgoods.limitedgoods.notification.entity.EmailDelivery;
import com.limitedgoods.limitedgoods.notification.repository.EmailDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailDeliveryStateService {
    private final EmailDeliveryRepository emailDeliveryRepository;

    @Transactional
    public List<ClaimedEmailDelivery> claimBatch(
            LocalDateTime now,
            LocalDateTime staleBefore,
            int maxAttempts,
            int batchSize
    ) {
        emailDeliveryRepository.markExhaustedProcessingAsDead(
                staleBefore,
                maxAttempts
        );

        List<EmailDelivery> deliveries =
                emailDeliveryRepository.lockClaimableDeliveries(
                        now,
                        staleBefore,
                        maxAttempts,
                        batchSize
                );

        List<ClaimedEmailDelivery> result = new ArrayList<>();

        for(EmailDelivery emailDelivery : deliveries) {
            result.add(new ClaimedEmailDelivery(
                    emailDelivery.getId(),
                    emailDelivery.markProcessing(now)
            ));
        }

        return result;
    }

    @Transactional
    public void markSent(
            Long deliveryId,
            UUID claimToken,
            LocalDateTime now
    ) {
        EmailDelivery delivery = getForUpdate(deliveryId);
        delivery.markSent(claimToken, now);
    }

    @Transactional
    public void markRetryableFailure(
            ClaimedEmailDelivery claim,
            Throwable exception,
            int maxAttempts,
            LocalDateTime now
    ) {
        EmailDelivery delivery = getForUpdate(claim.deliveryId());

        delivery.markRetryableFailure(
                claim.claimToken(),
                exception.getMessage(),
                maxAttempts,
                now
        );
    }

    @Transactional
    public void markPermanentFailure(
            ClaimedEmailDelivery claim,
            Throwable exception
    ) {
        EmailDelivery delivery = getForUpdate(claim.deliveryId());

        delivery.markPermanentFailure(
                claim.claimToken(),
                exception.getMessage()
        );
    }

    @Transactional
    public void releaseAfterInfrastructureFailure(
            ClaimedEmailDelivery claim,
            Throwable exception,
            LocalDateTime nextAttemptAt
    ) {
        EmailDelivery delivery = getForUpdate(claim.deliveryId());

        delivery.releaseAfterInfrastructureFailure(
                claim.claimToken(),
                exception.getMessage(),
                nextAttemptAt
        );
    }

    @Transactional
    public void releaseBatchAfterInfrastructureFailure(
            List<ClaimedEmailDelivery> claims,
            String reason,
            LocalDateTime nextAttemptAt
    ) {
        for (ClaimedEmailDelivery claim : claims) {
            EmailDelivery delivery = getForUpdate(claim.deliveryId());

            if (!delivery.isOwnedBy(claim.claimToken())) {
                continue;
            }

            delivery.releaseAfterInfrastructureFailure(
                    claim.claimToken(),
                    reason,
                    nextAttemptAt
            );
        }
    }

    private EmailDelivery getForUpdate(Long deliveryId) {
        return emailDeliveryRepository
                .findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Email delivery not found: " + deliveryId
                        )
                );
    }

}
