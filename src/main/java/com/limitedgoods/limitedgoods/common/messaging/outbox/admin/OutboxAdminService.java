package com.limitedgoods.limitedgoods.common.messaging.outbox.admin;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxDeadEventResponse;
import com.limitedgoods.limitedgoods.common.messaging.outbox.repository.OutboxJdbcRepository;
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
    private final OutboxJdbcRepository outboxJdbcRepository;

    public List<OutboxDeadEventResponse> findOutboxDeadEvents(int page, int size){
        return outboxJdbcRepository.findOutboxDeadEvents(page, size);

    }

    @Transactional
    public void requeue(Long adminUserId, UUID eventId) {
        boolean requeued = outboxJdbcRepository.requeueDead(eventId);
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
