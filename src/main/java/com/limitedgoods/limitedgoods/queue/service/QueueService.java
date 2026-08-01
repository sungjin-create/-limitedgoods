package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.queue.config.QueueAdmissionProperties;
import com.limitedgoods.limitedgoods.queue.dto.QueueAdmissionResult;
import com.limitedgoods.limitedgoods.queue.dto.QueueStatusResponse;
import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final AdmissionTokenService admissionTokenService;
    private final QueueMaintenanceService queueMaintenanceService;
    private final QueueProductStateCacheService queueProductStateCacheService;
    private final QueueAdmissionProperties admissionProperties;

    /**
     * 대기열 진입
     * 이미 등록된 경우 기존 순번 유지
     */
    public QueueStatusResponse enterQueue(Long userId, Long productId) {
        queueProductStateCacheService.validateQueueEntryAllowed(productId);

        QueueAdmissionResult result =
                admissionTokenService.enterQueueAndIssueToken(
                        userId,
                        productId,
                        admissionProperties.getActiveWindow()
                );

        return toResponse(result);
    }

    /**
     * 대기 상태 폴링
     */
    public QueueStatusResponse getStatus(Long userId, Long productId) {
        queueProductStateCacheService.validateQueueEntryAllowed(productId);

        QueueAdmissionResult result =
                admissionTokenService.getStatusAndIssueTokenIfEligible(
                        userId,
                        productId,
                        admissionProperties.getActiveWindow()
                );

        return toResponse(result);
    }

    /**
     * 대기열에서 제거
     * 호출 시점: 주문 생성 완료, 입장 토큰 TTL 만료
     */
    public void removeFromQueue(Long userId, Long productId) {
        redisTemplate.opsForZSet().remove(
                QueueRedisKeys.waiting(productId),
                userId.toString()
        );

        redisTemplate.opsForZSet().remove(
                QueueRedisKeys.activity(productId),
                userId.toString()
        );

        log.info(
                "event=queue_member_removed userId={} productId={}",
                userId,
                productId
        );
    }

    public void heartbeat(Long userId, Long productId) {
        boolean active = queueMaintenanceService.heartbeat(userId, productId);

        if (!active) {
            throw new BusinessException(ErrorCode.QUEUE_NOT_FOUND);
        }
    }

    public void leaveQueue(Long userId, Long productId) {
        LeaveResult result = queueMaintenanceService.leaveQueue(userId, productId);

        if (result == LeaveResult.PROCESSING) {
            throw new BusinessException(ErrorCode.QUEUE_LEAVE_NOT_ALLOWED);
        }

        // 이미 제거된 요청도 성공으로 처리해 DELETE를 멱등하게 만든다.
    }

    private QueueStatusResponse toResponse(
            QueueAdmissionResult result
    ) {
        if (result.admitted()) {
            return QueueStatusResponse.admitted(
                    result.admissionToken()
            );
        }

        return QueueStatusResponse.waiting(
                result.position()
        );
    }

}
