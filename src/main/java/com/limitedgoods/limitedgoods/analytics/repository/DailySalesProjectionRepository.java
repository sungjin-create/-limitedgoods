package com.limitedgoods.limitedgoods.analytics.repository;

import com.limitedgoods.limitedgoods.analytics.entity.DailySalesProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DailySalesProjectionRepository extends JpaRepository<DailySalesProjection, Long> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO daily_sales_projection (
                        sales_date,
                        paid_order_count,
                        gross_revenue,
                        refunded_order_count,
                        refund_amount,
                        sold_quantity
                    )
                    VALUES (
                        :salesDate,
                        1,
                        :grossRevenue,
                        0,
                        0,
                        :soldQuantity
                    )
                    ON CONFLICT (sales_date)
                    DO UPDATE SET
                        paid_order_count =
                            daily_sales_projection.paid_order_count + 1,
                        gross_revenue =
                            daily_sales_projection.gross_revenue
                                + EXCLUDED.gross_revenue,
                        sold_quantity =
                            daily_sales_projection.sold_quantity
                                + EXCLUDED.sold_quantity
                    """,
            nativeQuery = true
    )
    void addPaidOrder(
            @Param("salesDate") LocalDate salesDate,
            @Param("grossRevenue") long grossRevenue,
            @Param("soldQuantity") long soldQuantity
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO daily_sales_projection (
                        sales_date,
                        paid_order_count,
                        gross_revenue,
                        refunded_order_count,
                        refund_amount,
                        sold_quantity
                    )
                    VALUES (
                        :salesDate,
                        0,
                        0,
                        1,
                        :refundAmount,
                        0
                    )
                    ON CONFLICT (sales_date)
                    DO UPDATE SET
                        refunded_order_count =
                            daily_sales_projection.refunded_order_count + 1,
                        refund_amount =
                            daily_sales_projection.refund_amount
                                + EXCLUDED.refund_amount
                    """,
            nativeQuery = true
    )
    void addRefund(
            @Param("salesDate") LocalDate salesDate,
            @Param("refundAmount") long refundAmount
    );

    List<DailySalesProjection> findAllBySalesDateBetweenOrderBySalesDateAsc(
            LocalDate from,
            LocalDate to
    );
}