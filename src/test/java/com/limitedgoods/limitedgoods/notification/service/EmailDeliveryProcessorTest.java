package com.limitedgoods.limitedgoods.notification.service;

import com.limitedgoods.limitedgoods.notification.entity.EmailDelivery;
import com.limitedgoods.limitedgoods.notification.exception.EmailInfrastructureException;
import com.limitedgoods.limitedgoods.notification.exception.NonRetryableEmailException;
import com.limitedgoods.limitedgoods.notification.exception.RetryableEmailException;
import com.limitedgoods.limitedgoods.notification.infrastructure.mail.EmailProviderCircuit;
import com.limitedgoods.limitedgoods.notification.sendor.EmailSender;
import com.limitedgoods.limitedgoods.notification.repository.EmailDeliveryRepository;
import com.limitedgoods.limitedgoods.notification.template.EmailContent;
import com.limitedgoods.limitedgoods.notification.template.EmailTemplateData;
import com.limitedgoods.limitedgoods.notification.template.EmailTemplateKey;
import com.limitedgoods.limitedgoods.notification.template.EmailTemplateRegistry;
import com.limitedgoods.limitedgoods.notification.template.EmailTemplateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryProcessorTest {

    @Mock
    private EmailDeliveryRepository repository;

    @Mock
    private EmailSender emailSender;

    @Mock
    private EmailDeliveryStateService stateService;

    @Mock
    private EmailTemplateRegistry templateRegistry;

    private EmailProviderCircuit providerCircuit;
    private EmailDeliveryProcessor service;

    @BeforeEach
    void setUp() {
        providerCircuit = new EmailProviderCircuit();

        service = new EmailDeliveryProcessor(
                repository,
                emailSender,
                stateService,
                templateRegistry,
                providerCircuit
        );

        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(
                service,
                "infrastructureBackoffMs",
                300_000L
        );
    }

    @Test
    @DisplayName("이메일 발송에 성공하면 SENT 상태로 변경한다")
    void sendSuccess() {
        UUID claimToken = UUID.randomUUID();
        ClaimedEmailDelivery claim = new ClaimedEmailDelivery(1L, claimToken);

        EmailDelivery delivery = mockOwnedDelivery(claimToken);
        EmailTemplateKey key = templateKey();
        EmailTemplateData data = new EmailTemplateData(20L);
        EmailContent content =
                new EmailContent("결제 완료", "결제가 완료되었습니다.");

        when(delivery.getTemplateKey()).thenReturn(key);
        when(delivery.getTemplateData()).thenReturn(data);
        when(delivery.getRecipientEmail())
                .thenReturn("buyer@example.com");
        when(templateRegistry.render(key, data))
                .thenReturn(content);

        service.process(claim);

        verify(emailSender).send(
                "buyer@example.com",
                "결제 완료",
                "결제가 완료되었습니다."
        );
        verify(stateService).markSent(
                eq(1L),
                eq(claimToken),
                any(LocalDateTime.class)
        );
        verifyNoMoreInteractions(stateService);
    }

    @Test
    @DisplayName("claim 소유권이 없으면 이메일을 발송하지 않는다")
    void skipWhenClaimOwnershipWasLost() {
        UUID claimToken = UUID.randomUUID();
        ClaimedEmailDelivery claim = new ClaimedEmailDelivery(1L, claimToken);

        EmailDelivery delivery = mock(EmailDelivery.class);
        when(repository.findById(1L))
                .thenReturn(Optional.of(delivery));
        when(delivery.isOwnedBy(claimToken)).thenReturn(false);

        service.process(claim);

        verifyNoInteractions(emailSender);
        verifyNoInteractions(templateRegistry);
        verifyNoInteractions(stateService);
    }

    @Test
    @DisplayName("일시적인 이메일 실패는 재시도 가능한 실패로 처리한다")
    void handleRetryableFailure() {
        UUID claimToken = UUID.randomUUID();
        ClaimedEmailDelivery claim = new ClaimedEmailDelivery(1L, claimToken);

        EmailDelivery delivery = prepareRenderableDelivery(claimToken);
        RetryableEmailException exception =
                new RetryableEmailException(
                        "SMTP temporarily unavailable",
                        new RuntimeException()
                );

        doThrow(exception)
                .when(emailSender)
                .send(anyString(), anyString(), anyString());

        service.process(claim);

        verify(stateService).markRetryableFailure(
                eq(claim),
                same(exception),
                eq(5),
                any(LocalDateTime.class)
        );
        verify(stateService, never()).markSent(
                anyLong(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("재시도 불가능한 이메일 실패는 영구 실패로 처리한다")
    void handleNonRetryableFailure() {
        UUID claimToken = UUID.randomUUID();
        ClaimedEmailDelivery claim = new ClaimedEmailDelivery(1L, claimToken);

        prepareRenderableDelivery(claimToken);

        NonRetryableEmailException exception =
                new NonRetryableEmailException(
                        "Invalid recipient",
                        new RuntimeException()
                );

        doThrow(exception)
                .when(emailSender)
                .send(anyString(), anyString(), anyString());

        service.process(claim);

        verify(stateService).markPermanentFailure(
                claim,
                exception
        );
        verify(stateService, never()).markSent(
                anyLong(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("이메일 인프라 장애가 발생하면 claim을 반환하고 Circuit을 연다")
    void handleInfrastructureFailure() {
        UUID claimToken = UUID.randomUUID();
        ClaimedEmailDelivery claim = new ClaimedEmailDelivery(1L, claimToken);

        prepareRenderableDelivery(claimToken);

        EmailInfrastructureException exception =
                new EmailInfrastructureException(
                        "SMTP authentication failed",
                        new RuntimeException()
                );

        doThrow(exception)
                .when(emailSender)
                .send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.process(claim))
                .isSameAs(exception);

        verify(stateService)
                .releaseAfterInfrastructureFailure(
                        eq(claim),
                        same(exception),
                        any(LocalDateTime.class)
                );

        assertThat(providerCircuit.isBlocked(Instant.now()))
                .isTrue();

        verify(stateService, never()).markSent(
                anyLong(),
                any(),
                any()
        );
    }

    private EmailDelivery mockOwnedDelivery(UUID claimToken) {
        EmailDelivery delivery = mock(EmailDelivery.class);

        when(repository.findById(1L))
                .thenReturn(Optional.of(delivery));
        when(delivery.isOwnedBy(claimToken)).thenReturn(true);

        return delivery;
    }

    private EmailDelivery prepareRenderableDelivery(
            UUID claimToken
    ) {
        EmailDelivery delivery = mockOwnedDelivery(claimToken);
        EmailTemplateKey key = templateKey();
        EmailTemplateData data = new EmailTemplateData(20L);

        when(delivery.getTemplateKey()).thenReturn(key);
        when(delivery.getTemplateData()).thenReturn(data);
        when(delivery.getRecipientEmail())
                .thenReturn("buyer@example.com");

        when(templateRegistry.render(key, data))
                .thenReturn(
                        new EmailContent(
                                "결제 완료",
                                "결제가 완료되었습니다."
                        )
                );

        return delivery;
    }

    private EmailTemplateKey templateKey() {
        return new EmailTemplateKey(
                EmailTemplateType.PAYMENT_COMPLETED,
                1
        );
    }
}
