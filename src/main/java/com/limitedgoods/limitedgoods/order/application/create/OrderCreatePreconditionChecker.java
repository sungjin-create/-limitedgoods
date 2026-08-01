package com.limitedgoods.limitedgoods.order.application.create;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.dto.request.OrderItemRequest;
import com.limitedgoods.limitedgoods.order.policy.OrderProductValidationResult;
import com.limitedgoods.limitedgoods.order.dto.request.OrderRequest;
import com.limitedgoods.limitedgoods.order.policy.ProductOrderPolicy;
import com.limitedgoods.limitedgoods.order.infrastructure.ratelimit.OrderRateLimiter;
import com.limitedgoods.limitedgoods.product.service.ProductSoldOutCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OrderCreatePreconditionChecker {

    private final OrderRateLimiter orderRateLimiter;
    private final ProductSoldOutCacheService productSoldOutCacheService;
    private final ProductOrderPolicy productOrderPolicy;

    public void validateRequest(OrderRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (request.checkoutToken() == null || request.checkoutToken().isBlank()) {
            throw new BusinessException( ErrorCode.HAS_NO_CHECKOUT_TOKEN);
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Set<Long> productIds = new HashSet<>();

        for (OrderItemRequest item : request.items()) {
            if (item == null
                    || item.productId() == null
                    || item.quantity() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            if (!productIds.add(item.productId())) {
                throw new BusinessException(
                        ErrorCode.DUPLICATE_ORDER_PRODUCT
                );
            }
        }
    }

    /**
     * 신규 주문에만 필요한 외부 상태 및 상품 정책 검증
     */
    public OrderProductValidationResult checkNewOrder(Long userId, OrderRequest request) {
        validateRateLimit(userId, request);
        validateSoldOutCache(request);

        return productOrderPolicy.validate(request.items());
    }

    private void validateRateLimit(Long userId, OrderRequest request) {
        for (OrderItemRequest item : request.items()) {
            boolean allowed = orderRateLimiter.allow(userId, item.productId());

            if (!allowed) {
                throw new BusinessException(ErrorCode.TOO_MANY_ORDER_REQUESTS);
            }
        }
    }

    private void validateSoldOutCache(OrderRequest request) {
        for (OrderItemRequest item : request.items()) {
            boolean soldOut = productSoldOutCacheService.isSoldOut(item.productId());

            if (soldOut) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }
    }
}
