package com.limitedgoods.limitedgoods.order.entity;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "total_price")
    private long totalPrice;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "cancel_fail_reason")
    private String cancelFailReason;

    @Column(name = "checkout_token")
    private String checkoutToken;

    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;

    public static Order create(User user,
                               long totalPrice,
                               LocalDateTime expiresAt,
                               String checkoutToken,
                               String requestFingerprint) {
        LocalDateTime now = LocalDateTime.now();

        return Order.builder()
                .user(user)
                .totalPrice(totalPrice)
                .status(OrderStatus.CREATED)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(expiresAt)
                .checkoutToken(checkoutToken)
                .requestFingerprint(requestFingerprint)
                .build();
    }

    public void markPaymentPending(LocalDateTime now) {
        if (this.status != OrderStatus.CREATED
                && this.status != OrderStatus.PAYMENT_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "현재 주문 상태 = " + this.status);
        }

        if (this.expiresAt == null || !this.expiresAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
        }

        this.status = OrderStatus.PAYMENT_PENDING;
        this.updatedAt = now;
        this.failReason = null;
        this.failedAt = null;
        this.updatedAt = LocalDateTime.now();
        this.failReason = null;
        this.failedAt = null;
    }

    public void markExpired(LocalDateTime now) {
        if (this.status != OrderStatus.CREATED
                && this.status != OrderStatus.PAYMENT_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "현재 주문 상태 = " + this.status);
        }

        this.status = OrderStatus.EXPIRED;
        this.updatedAt = now;
    }

    public void markPaid() {
        validateCurrentStatus(OrderStatus.PAYMENT_APPROVED);
        LocalDateTime now = LocalDateTime.now();
        this.status = OrderStatus.PAID;
        this.paidAt = now;
        this.updatedAt = now;
    }

    public void markPaymentFailed(String reason) {
        validateCurrentStatus(OrderStatus.PAYMENT_PENDING);
        LocalDateTime now = LocalDateTime.now();
        this.status = OrderStatus.PAYMENT_FAILED;
        this.failReason = reason;
        this.failedAt = now;
        this.updatedAt = now;
    }

    public void retryCancel(){
        validateCurrentStatus(OrderStatus.CANCEL_FAILED);

        this.status = OrderStatus.CANCEL_REQUESTED;
        this.cancelFailReason = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void markComplete() {
        validateCurrentStatus(OrderStatus.PAID);
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    private void validateCurrentStatus(OrderStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "현재 주문 상태 = " + this.status);
        }
    }

    public void markPaymentApproved() {
        validateCurrentStatus(OrderStatus.PAYMENT_PENDING);
        this.status = OrderStatus.PAYMENT_APPROVED;
        this.updatedAt = LocalDateTime.now();
    }

    public void requestCancel() {
        validateCurrentStatus(OrderStatus.PAID);

        this.status = OrderStatus.CANCEL_REQUESTED;
        this.cancelRequestedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.cancelFailReason = null;
    }

    public void markRefunded() {
        if (this.status != OrderStatus.CANCEL_REQUESTED
                && this.status != OrderStatus.CANCEL_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "현재 주문 상태 = " + this.status);
        }

        this.status = OrderStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.cancelFailReason = null;
    }

    public void markCancelFailed(String reason) {
        validateCurrentStatus(OrderStatus.CANCEL_REQUESTED);

        this.status = OrderStatus.CANCEL_FAILED;
        this.cancelFailReason = reason;
        this.updatedAt = LocalDateTime.now();
    }
}
