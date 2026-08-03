package com.limitedgoods.limitedgoods.payment.repository;

import com.limitedgoods.limitedgoods.payment.entity.RefundAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    Optional<RefundAttempt> findTopByOrder_IdOrderByIdDesc(Long orderId);
}
