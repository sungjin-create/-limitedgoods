package com.limitedgoods.limitedgoods.backoffice.dashboard.repository;

import com.limitedgoods.limitedgoods.backoffice.dashboard.dto.BackofficeRecentOrderResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BackofficeDashboardQueryRepository extends JpaRepository<Order, Long> {
    @Query("""
    select new com.limitedgoods.limitedgoods.backoffice.dashboard.dto.BackofficeRecentOrderResponse(
        o.id,
        u.email,
        min(p.name),
        count(oi.id),
        o.totalPrice,
        o.status,
        o.createdAt
    )
    from Order o
    join o.user u
    join OrderItem oi on oi.order = o
    join oi.product p
    group by
        o.id,
        u.email,
        o.totalPrice,
        o.status,
        o.createdAt
    order by o.createdAt desc, o.id desc
    """)
    List<BackofficeRecentOrderResponse> findRecentOrders(Pageable pageable);
}
