package com.limitedgoods.limitedgoods.messaging.outbox.admin;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.messaging.outbox.admin.dto.OutboxDeadEventResponse;
import com.limitedgoods.limitedgoods.messaging.outbox.infrastructure.jdbc.JdbcOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxAdminService {
    private final JdbcOutboxRepository outboxRepository;

    public List<OutboxDeadEventResponse> findOutboxDeadEvents(int page, int size){
        return outboxRepository.findOutboxDeadEvents(page, size);

    }

    @Transactional
    public void requeue(Long adminUserId, UUID eventId) {
        boolean requeued = outboxRepository.requeueDead(eventId);
        if (!requeued) {
            throw new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_REQUEUEABLE);
        }
        log.info(
                "event=outbox_requeued adminUserId={} eventId={}",
                adminUserId,
                eventId
        );
    }
}
