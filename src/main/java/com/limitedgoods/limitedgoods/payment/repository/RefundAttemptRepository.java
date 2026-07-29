package com.limitedgoods.limitedgoods.payment.repository;

import com.limitedgoods.limitedgoods.payment.entity.RefundAttempt;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefundAttemptRepository extends JpaRepository<RefundAttempt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select attempt
        from RefundAttempt attempt
        join fetch attempt.order
        join fetch attempt.paymentAttempt
        where attempt.id = :attemptId
    """)
    Optional<RefundAttempt> findByIdForUpdate(
            @Param("attemptId") Long attemptId
    );

    @Query("""
        select attempt
        from RefundAttempt attempt
        where attempt.order.id = :orderId
          and attempt.status in :statuses
        order by attempt.id desc
    """)
    List<RefundAttempt> findActiveByOrderId(
            @Param("orderId") Long orderId,
            @Param("statuses") List<RefundAttemptStatus> statuses,
            Pageable pageable
    );

    default Optional<RefundAttempt> findLatestActive(Long orderId) {
        return findActiveByOrderId(
                orderId,
                List.of(
                        RefundAttemptStatus.PROCESSING,
                        RefundAttemptStatus.UNKNOWN
                ),
                Pageable.ofSize(1)
        ).stream().findFirst();
    }

    @Query("""
        select attempt.id
        from RefundAttempt attempt
        where attempt.status in :statuses
          and attempt.manualReviewRequired = false
          and attempt.nextReconcileAt <= :now
        order by attempt.nextReconcileAt, attempt.id
    """)
    List<Long> findReconciliationCandidateIds(
            @Param("statuses") List<RefundAttemptStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    Optional<RefundAttempt> findTopByOrder_IdOrderByIdDesc(Long orderId);
}