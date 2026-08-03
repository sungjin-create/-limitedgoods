package com.limitedgoods.limitedgoods.product.policy;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.limitedgoods.limitedgoods.product.entity.ProductStatus.*;
import static com.limitedgoods.limitedgoods.product.entity.ProductStatus.ACTIVE;

@Component
@RequiredArgsConstructor
public class ProductStatusPolicy {

    private static final Map<ProductStatus, Set<ProductStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    DRAFT, EnumSet.of(PREPARING, SCHEDULED, ACTIVE),
                    PREPARING, EnumSet.of(SCHEDULED, ACTIVE, PAUSED, HIDDEN, ARCHIVED),
                    SCHEDULED, EnumSet.of(ACTIVE, PAUSED, HIDDEN, ARCHIVED),
                    ACTIVE, EnumSet.of(SCHEDULED, PAUSED, HIDDEN, ARCHIVED),
                    PAUSED, EnumSet.of(PREPARING, SCHEDULED, ACTIVE, ARCHIVED),
                    HIDDEN, EnumSet.of(PREPARING, SCHEDULED, ACTIVE, ARCHIVED),
                    ARCHIVED, EnumSet.noneOf(ProductStatus.class)
            );

    public void validateRegisterStatus(ProductStatus status) {
        if(status != DRAFT && status != PREPARING && status != SCHEDULED && status != ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS_REGISTER);
        }
    }

    public void validateSaleSchedule(
            ProductStatus nextStatus,
            LocalDateTime saleStartAt,
            LocalDateTime saleEndAt
    ) {
        if (saleStartAt != null && saleEndAt != null
                && !saleStartAt.isBefore(saleEndAt)) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_TIME);
        }

        if (nextStatus == ProductStatus.SCHEDULED) {
            if (saleStartAt == null) {
                throw new BusinessException(ErrorCode.HAS_NO_SALE_TIME);
            }

            if (!saleStartAt.isAfter(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.SALE_START_MUST_BE_FUTURE);
            }
        }

        if (nextStatus == ProductStatus.ACTIVE
                && saleEndAt != null
                && !saleEndAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SALE_ALREADY_ENDED);
        }
    }

    public void validateTransition(ProductStatus currentStatus, ProductStatus nextStatus) {
        if (currentStatus == nextStatus) {
            return;
        }

        Set<ProductStatus> allowed = ALLOWED_TRANSITIONS.get(currentStatus);

        if (allowed == null || !allowed.contains(nextStatus)) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION);
        }
    }

}
