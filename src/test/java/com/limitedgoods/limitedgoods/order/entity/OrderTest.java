package com.limitedgoods.limitedgoods.order.entity;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void create_initializesReservationAndIdempotencyFields() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        Order order = Order.create(new User(), 25_000L, expiresAt, "checkout-1", "fingerprint");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalPrice()).isEqualTo(25_000L);
        assertThat(order.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(order.getCheckoutToken()).isEqualTo("checkout-1");
        assertThat(order.getRequestFingerprint()).isEqualTo("fingerprint");
        assertThat(order.getCreatedAt()).isNotNull();
    }

    @Test
    void paymentTransitions_createdToPaid() {
        Order order = newOrder();

        order.markPaymentPending(LocalDateTime.now());
        order.markPaymentApproved();
        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
    }

    @Test
    void failedPaymentCanRetryBeforeReservationExpires() {
        Order order = newOrder();
        order.markPaymentPending(LocalDateTime.now());
        order.markPaymentFailed("declined");

        order.markPaymentPending(LocalDateTime.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getFailReason()).isNull();
        assertThat(order.getFailedAt()).isNull();
    }

    @Test
    void paymentCannotStartAfterReservationExpires() {
        Order order = Order.create(
                new User(), 10_000L, LocalDateTime.now().minusSeconds(1),
                "checkout-expired", "fingerprint"
        );

        assertBusinessError(
                () -> order.markPaymentPending(LocalDateTime.now()),
                ErrorCode.RESERVATION_EXPIRED
        );
    }

    @Test
    void refundFailureCanBeRetriedAndCompleted() {
        Order order = newOrder();
        order.markPaymentPending(LocalDateTime.now());
        order.markPaymentApproved();
        order.markPaid();
        order.requestCancel();
        order.markCancelFailed("provider unavailable");

        order.retryCancel();
        order.markRefunded();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getRefundedAt()).isNotNull();
        assertThat(order.getCancelFailReason()).isNull();
    }

    @Test
    void invalidTransitionIsRejected() {
        Order order = newOrder();

        assertBusinessError(order::markPaid, ErrorCode.INVALID_ORDER_STATUS);
    }

    private Order newOrder() {
        return Order.create(
                new User(), 10_000L, LocalDateTime.now().plusMinutes(5),
                "checkout", "fingerprint"
        );
    }

    private void assertBusinessError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
