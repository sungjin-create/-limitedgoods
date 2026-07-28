package com.limitedgoods.limitedgoods.notification.handler;

import com.limitedgoods.limitedgoods.event.payload.order.OrderPaidEvent;
import com.limitedgoods.limitedgoods.event.idempotency.entity.ProcessedEvent;
import com.limitedgoods.limitedgoods.event.idempotency.repository.ProcessedEventRepository;
import com.limitedgoods.limitedgoods.notification.entity.EmailDelivery;
import com.limitedgoods.limitedgoods.notification.repository.EmailDeliveryRepository;
import com.limitedgoods.limitedgoods.notification.template.EmailTemplateCatalog;
import com.limitedgoods.limitedgoods.notification.template.EmailTemplateKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalEmailEventHandler {

    private static final String CONSUMER_NAME = "email";

    private final EmailDeliveryRepository emailDeliveryRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(Long eventId, OrderPaidEvent event) {
        if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            return;
        }

        EmailTemplateKey templateKey = EmailTemplateCatalog.PAYMENT_COMPLETED_V1;

        EmailDelivery delivery = new EmailDelivery(
                eventId,
                event.orderId(),
                event.recipientEmail(),
                templateKey.type(),
                templateKey.version()
        );

        emailDeliveryRepository.save(delivery);

        processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER_NAME));
    }
}