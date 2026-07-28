package com.limitedgoods.limitedgoods.analytics.repository;

import com.limitedgoods.limitedgoods.analytics.entity.ProductSalesProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductSalesProjectionRepository extends JpaRepository<ProductSalesProjection, Long> {

    Optional<ProductSalesProjection> findByProductId(
            Long productId
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO product_sales_projection (
                        product_id,
                        paid_order_count,
                        sold_quantity,
                        gross_revenue,
                        refunded_quantity,
                        refund_amount,
                        last_sold_at
                    )
                    VALUES (
                        :productId,
                        1,
                        :quantity,
                        :revenue,
                        0,
                        0,
                        :paidAt
                    )
                    ON CONFLICT (product_id)
                    DO UPDATE SET
                        paid_order_count =
                            product_sales_projection.paid_order_count + 1,
                        sold_quantity =
                            product_sales_projection.sold_quantity
                                + EXCLUDED.sold_quantity,
                        gross_revenue =
                            product_sales_projection.gross_revenue
                                + EXCLUDED.gross_revenue,
                        last_sold_at =
                            GREATEST(
                                product_sales_projection.last_sold_at,
                                EXCLUDED.last_sold_at
                            )
                    """,
            nativeQuery = true
    )
    void addSale(
            @Param("productId") Long productId,
            @Param("quantity") int quantity,
            @Param("revenue") long revenue,
            @Param("paidAt") LocalDateTime paidAt
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO product_sales_projection (
                        product_id,
                        paid_order_count,
                        sold_quantity,
                        gross_revenue,
                        refunded_quantity,
                        refund_amount,
                        last_sold_at
                    )
                    VALUES (
                        :productId,
                        0,
                        0,
                        0,
                        :quantity,
                        :refundAmount,
                        NULL
                    )
                    ON CONFLICT (product_id)
                    DO UPDATE SET
                        refunded_quantity =
                            product_sales_projection.refunded_quantity
                                + EXCLUDED.refunded_quantity,
                        refund_amount =
                            product_sales_projection.refund_amount
                                + EXCLUDED.refund_amount
                    """,
            nativeQuery = true
    )
    void addRefund(
            @Param("productId") Long productId,
            @Param("quantity") int quantity,
            @Param("refundAmount") long refundAmount
    );

    @Query(
            value = """
                WITH product_activity AS (
                    SELECT
                        item.product_id,
                        COUNT(DISTINCT orders.id) AS paid_order_count,
                        SUM(item.quantity) AS sold_quantity,
                        SUM(item.line_total_price) AS gross_revenue,
                        0::BIGINT AS refunded_quantity,
                        0::BIGINT AS refund_amount,
                        MAX(orders.paid_at) AS last_sold_at
                    FROM orders
                    JOIN order_items item
                      ON item.order_id = orders.id
                    WHERE orders.paid_at >= :fromAt
                      AND orders.paid_at < :toExclusive
                    GROUP BY item.product_id

                    UNION ALL

                    SELECT
                        item.product_id,
                        0::BIGINT AS paid_order_count,
                        0::BIGINT AS sold_quantity,
                        0::BIGINT AS gross_revenue,
                        SUM(item.quantity) AS refunded_quantity,
                        SUM(item.line_total_price) AS refund_amount,
                        NULL::TIMESTAMP AS last_sold_at
                    FROM orders
                    JOIN order_items item
                      ON item.order_id = orders.id
                    WHERE orders.refunded_at >= :fromAt
                      AND orders.refunded_at < :toExclusive
                    GROUP BY item.product_id
                )
                SELECT
                    product.id AS "productId",
                    product.name AS "productName",
                    SUM(activity.paid_order_count) AS "paidOrderCount",
                    SUM(activity.sold_quantity) AS "soldQuantity",
                    SUM(activity.refunded_quantity) AS "refundedQuantity",
                    SUM(activity.gross_revenue) AS "grossRevenue",
                    SUM(activity.refund_amount) AS "refundAmount",
                    MAX(activity.last_sold_at) AS "lastSoldAt"
                FROM product_activity activity
                JOIN product
                  ON product.id = activity.product_id
                GROUP BY product.id, product.name
                ORDER BY
                    (SUM(activity.gross_revenue) - SUM(activity.refund_amount)) DESC,
                    (SUM(activity.sold_quantity) - SUM(activity.refunded_quantity)) DESC,
                    product.id ASC
                LIMIT :limit
                """,
            nativeQuery = true
    )
    List<ProductSalesRankView> findTopSellingProducts(
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("limit") int limit
    );

    interface ProductSalesRankView {

        Long getProductId();

        String getProductName();

        long getPaidOrderCount();

        long getSoldQuantity();

        long getRefundedQuantity();

        long getGrossRevenue();

        long getRefundAmount();

        LocalDateTime getLastSoldAt();
    }
}
