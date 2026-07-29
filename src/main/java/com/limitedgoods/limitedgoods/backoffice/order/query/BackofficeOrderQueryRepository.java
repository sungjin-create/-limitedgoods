package com.limitedgoods.limitedgoods.backoffice.order.query;

import com.limitedgoods.limitedgoods.backoffice.order.dto.OrderSummaryResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BackofficeOrderQueryRepository extends JpaRepository<Order, Long>{

    @Query("""
    select new com.limitedgoods.limitedgoods.backoffice.order.dto.OrderSummaryResponse(
        coalesce(sum(case when o.status in ('PAYMENT_PENDING', 'PAYMENT_APPROVED') then 1 else 0 end), 0),
        coalesce(sum(case when o.status in ('PAID', 'COMPLETED') then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'CANCEL_REQUESTED' then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'REFUNDED' then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'PAYMENT_FAILED' then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'EXPIRED' then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'CANCEL_FAILED' then 1 else 0 end), 0)
    )
    from Order o
    """)
    OrderSummaryResponse getBackofficeOrderSummary();

    @Query("""
    select new com.limitedgoods.limitedgoods.backoffice.order.dto.OrderSummaryResponse(
                coalesce(sum(case when o.status in ('PAYMENT_PENDING', 'PAYMENT_APPROVED') then 1 else 0 end), 0),
        coalesce(sum(case when o.status in ('PAID', 'COMPLETED') then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'CANCEL_REQUESTED' then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'REFUNDED' then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'PAYMENT_FAILED' then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'EXPIRED' then 1 else 0 end), 0),
        coalesce(sum(case when o.status = 'CANCEL_FAILED' then 1 else 0 end), 0)
    )
    from Order o
    where o.createdAt >= :startAt
      and o.createdAt < :endAt
    """)
    OrderSummaryResponse getBackofficeOrderSummary(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query("""
    select new com.limitedgoods.limitedgoods.backoffice.order.query.OrderFlatQueryResult(
        o.id,
        u.email,
        o.totalPrice,
        o.status,
        o.createdAt,
        p.id,
        p.name,
        oi.quantity,
        oi.price
    )
    from OrderItem oi
    join oi.order o
    join o.user u
    join oi.product p
    order by o.createdAt desc
    """)
    List<OrderFlatQueryResult> findBackofficeOrdersFlat();

    @Query("""
    select new com.limitedgoods.limitedgoods.backoffice.order.query.OrderFlatQueryResult(
        o.id,
        u.email,
        o.totalPrice,
        o.status,
        o.createdAt,
        p.id,
        p.name,
        oi.quantity,
        oi.price
    )
    from OrderItem oi
    join oi.order o
    join o.user u
    join oi.product p
    where (o.createdAt >= :startAt)
      and (o.createdAt <= :endAt)
    order by o.createdAt desc
    """)
    List<OrderFlatQueryResult> findBackofficeOrdersFlat(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

}
