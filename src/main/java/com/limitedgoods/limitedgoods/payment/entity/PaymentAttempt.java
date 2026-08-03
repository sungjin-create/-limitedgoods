package com.limitedgoods.limitedgoods.payment.entity;

import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.payment.dto.PaymentResult;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "payment_attempt",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_payment_attempt_order_key",
                        columnNames = {"order_id", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uq_payment_attempt_pg_transaction",
                        columnNames = {"pg_transaction_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptStatus status;

    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "approved_amount")
    private Long approvedAmount;

    @Column(name = "compensation_required", nullable = false)
    private boolean compensationRequired;

    public static PaymentAttempt create(
            Order order,
            String idempotencyKey,
            String requestFingerprint
    ) {
        LocalDateTime now = LocalDateTime.now();

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.order = order;
        attempt.idempotencyKey = idempotencyKey;
        attempt.requestFingerprint = requestFingerprint;
        attempt.amount = order.getTotalPrice();
        attempt.status = PaymentAttemptStatus.PROCESSING;
        attempt.requestedAt = now;
        attempt.updatedAt = now;
        return attempt;
    }

    public void approve(PaymentResult result) {
        this.status = PaymentAttemptStatus.APPROVED;
        this.pgTransactionId = result.transactionId();
        this.approvedAmount = result.approvedAmount();
        this.approvedAt = result.approvedAt();
        this.updatedAt = LocalDateTime.now();
    }

    public void decline(String code, String reason) {
        this.status = PaymentAttemptStatus.DECLINED;
        this.failureCode = code;
        this.failureReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void markUnknown(String reason) {
        this.status = PaymentAttemptStatus.UNKNOWN;
        this.failureReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAmountMismatch(PaymentResult result) {
        status = PaymentAttemptStatus.UNKNOWN;
        pgTransactionId = result.transactionId();
        approvedAmount = result.approvedAmount();
        approvedAt = result.approvedAt();
        failureCode = "PAYMENT_AMOUNT_MISMATCH";
        failureReason = "주문 금액과 PG 승인 금액 불일치";
        compensationRequired = true;
        updatedAt = LocalDateTime.now();
    }
}
