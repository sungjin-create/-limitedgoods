package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.queue.dto.QueueAdmissionResult;
import com.limitedgoods.limitedgoods.product.service.ProductSoldOutCacheService;
import com.limitedgoods.limitedgoods.queue.dto.QueueStatusResponse;
import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductSoldOutCacheService productSoldOutCacheService;
    private final AdmissionTokenService admissionTokenService;
    private final QueueMaintenanceService queueMaintenanceService;
    private final ProductRepository productRepository;

    private static final int    ACTIVE_WINDOW = 50;

    /**
     * 대기열 진입
     * 이미 등록된 경우 기존 순번 유지
     */
    public QueueStatusResponse enterQueue(Long userId, Long productId) {
        validateProductForQueue(productId);

        queueMaintenanceService.registerActiveProduct(productId);

        QueueAdmissionResult result =
                admissionTokenService.enterQueueAndIssueToken(
                        userId,
                        productId,
                        ACTIVE_WINDOW
                );

        return toResponse(result);
    }

    /**
     * 대기 상태 폴링
     */
    public QueueStatusResponse getStatus(Long userId, Long productId) {
        return enterQueue(userId, productId);
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
        LeaveResult result = queueMaintenanceService.leave(userId, productId);

        if (result == LeaveResult.PROCESSING) {
            throw new BusinessException(ErrorCode.QUEUE_LEAVE_NOT_ALLOWED);
        }

        // 이미 제거된 요청도 성공으로 처리해 DELETE를 멱등하게 만든다.
    }

    private void validateProductForQueue(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_ID);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PRODUCT_ID));

        if (product.getType() != ProductType.LIMITED) {
            throw new BusinessException(
                    ErrorCode.QUEUE_PRODUCT_NOT_SUPPORTED
            );
        }

        if (!product.isPurchasableAt(LocalDateTime.now())) {
            queueMaintenanceService.clearProductQueue(productId);
            throw new BusinessException(ErrorCode.QUEUE_CLOSED);
        }

        /*
         * Redis 캐시가 존재하거나 DB 실제 재고가 0이면 품절이다.
         *
         * DB 상품은 판매 상태와 유형 확인을 위해 어차피 조회하므로,
         * 캐시 미스 여부와 관계없이 실제 재고도 함께 확인하는 편이 안전하다.
         */
        boolean cachedSoldOut = productSoldOutCacheService.isSoldOut(productId);

        if (cachedSoldOut || product.getStock() <= 0) {
            if (!cachedSoldOut) {
                productSoldOutCacheService.markSoldOut(productId);
            }

            queueMaintenanceService.clearProductQueue(productId);
            throw new BusinessException(ErrorCode.QUEUE_SOLD_OUT);
        }
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
