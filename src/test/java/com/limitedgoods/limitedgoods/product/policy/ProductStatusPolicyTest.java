package com.limitedgoods.limitedgoods.product.policy;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductStatusPolicyTest {

    private final ProductStatusPolicy policy = new ProductStatusPolicy();

    @Test
    void registerAllowsOnlyInitialStatuses() {
        for (ProductStatus status : new ProductStatus[]{
                ProductStatus.DRAFT, ProductStatus.PREPARING,
                ProductStatus.SCHEDULED, ProductStatus.ACTIVE
        }) {
            assertThatCode(() -> policy.validateRegisterStatus(status)).doesNotThrowAnyException();
        }

        assertBusinessError(
                () -> policy.validateRegisterStatus(ProductStatus.PAUSED),
                ErrorCode.INVALID_PRODUCT_STATUS_REGISTER
        );
    }

    @Test
    void scheduledProductRequiresFutureStart() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = start.plusHours(2);

        assertThatCode(() -> policy.validateSaleSchedule(ProductStatus.SCHEDULED, start, end))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateSaleSchedule(ProductStatus.SCHEDULED, start, null))
                .doesNotThrowAnyException();
        assertBusinessError(
                () -> policy.validateSaleSchedule(ProductStatus.SCHEDULED, null, end),
                ErrorCode.HAS_NO_SALE_TIME
        );
        assertBusinessError(
                () -> policy.validateSaleSchedule(
                        ProductStatus.SCHEDULED, LocalDateTime.now().minusMinutes(1), end),
                ErrorCode.SALE_START_MUST_BE_FUTURE
        );
    }

    @Test
    void invalidPeriodAndEndedActiveProductAreRejected() {
        LocalDateTime now = LocalDateTime.now();

        assertBusinessError(
                () -> policy.validateSaleSchedule(ProductStatus.ACTIVE, now.plusHours(1), now),
                ErrorCode.INVALID_PRODUCT_TIME
        );
        assertBusinessError(
                () -> policy.validateSaleSchedule(ProductStatus.ACTIVE, null, now.minusSeconds(1)),
                ErrorCode.SALE_ALREADY_ENDED
        );
    }

    @Test
    void transitionPolicyAllowsDocumentedPathsAndRejectsArchivedRecovery() {
        assertThatCode(() -> policy.validateTransition(ProductStatus.DRAFT, ProductStatus.ACTIVE))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateTransition(ProductStatus.PAUSED, ProductStatus.SCHEDULED))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateTransition(ProductStatus.ACTIVE, ProductStatus.SCHEDULED))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateTransition(ProductStatus.ACTIVE, ProductStatus.ACTIVE))
                .doesNotThrowAnyException();

        assertBusinessError(
                () -> policy.validateTransition(ProductStatus.ARCHIVED, ProductStatus.ACTIVE),
                ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION
        );
    }

    private void assertBusinessError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
