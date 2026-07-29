package com.limitedgoods.limitedgoods.analytics.repository;

import com.limitedgoods.limitedgoods.analytics.entity.DailyOrderFunnelProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DailyOrderFunnelProjectionRepository extends JpaRepository<DailyOrderFunnelProjection, Long> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO daily_order_funnel_projection (
                        order_date,
                        created_order_count,
                        paid_order_count,
                        payment_failure_attempt_count,
                        expired_order_count,
                        refunded_order_count
                    )
                    VALUES (
                        :orderDate,
                        :createdCount,
                        :paidCount,
                        :failureCount,
                        :expiredCount,
                        :refundedCount
                    )
                    ON CONFLICT (order_date)
                    DO UPDATE SET
                        created_order_count =
                            daily_order_funnel_projection.created_order_count
                                + EXCLUDED.created_order_count,
                        paid_order_count =
                            daily_order_funnel_projection.paid_order_count
                                + EXCLUDED.paid_order_count,
                        payment_failure_attempt_count =
                            daily_order_funnel_projection.payment_failure_attempt_count
                                + EXCLUDED.payment_failure_attempt_count,
                        expired_order_count =
                            daily_order_funnel_projection.expired_order_count
                                + EXCLUDED.expired_order_count,
                        refunded_order_count =
                            daily_order_funnel_projection.refunded_order_count
                                + EXCLUDED.refunded_order_count
                    """,
            nativeQuery = true
    )
    void increment(
            @Param("orderDate") LocalDate orderDate,
            @Param("createdCount") long createdCount,
            @Param("paidCount") long paidCount,
            @Param("failureCount") long failureCount,
            @Param("expiredCount") long expiredCount,
            @Param("refundedCount") long refundedCount
    );

    List<DailyOrderFunnelProjection> findAllByOrderDateBetweenOrderByOrderDateAsc(
            LocalDate from,
            LocalDate to
    );
}