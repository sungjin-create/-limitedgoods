package com.limitedgoods.limitedgoods.order.repository;

import com.limitedgoods.limitedgoods.order.dto.response.OrderSummaryResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatusInAndExpiresAtBefore(
            List<OrderStatus> statuses,
            LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from Order o
        where o.id = :id
          and o.user.id = :userId
        """)
    Optional<Order> findByIdForUpdate(
            @Param("id") Long id,
            @Param("userId") Long userId);

    @Query("""
    select o
    from Order o
    where o.id = :orderId
      and o.user.id = :userId
    """)
    Optional<Order> findByIdAndUserId(
            @Param("orderId") Long orderId,
            @Param("userId") Long userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Order o
           set o.status = :expiredStatus,
               o.updatedAt = :now
         where o.id = :orderId
           and o.status in :activeStatuses
        """)
    int expireIfActive(
            @Param("orderId") Long orderId,
            @Param("expiredStatus") OrderStatus expiredStatus,
            @Param("activeStatuses") List<OrderStatus> activeStatuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
    select new com.limitedgoods.limitedgoods.order.dto.response.OrderSummaryResponse(
        o.id,
        o.totalPrice,
        o.status,
        o.createdAt,
        o.expiresAt
    )
    from Order o
    where o.user.id = :userId
    order by o.createdAt desc, o.id desc
    """)
    List<OrderSummaryResponse> findMyOrderSummaries(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select o
    from Order o
    where o.user.id = :userId
        and o.status in :statuses
    order by o.id
    """)
    List<Order> findActiveOrdersForUpdate(
            @Param("userId") Long userId,
            @Param("statuses") List<OrderStatus> orderStatusList
    );

    @Query("""
    select o
    from Order o
    where o.user.id = :userId
        and o.checkoutToken = :checkoutToken
    """)
    Optional<Order> findByUserIdAndCheckoutToken(
            @Param("userId") Long userId,
            @Param("checkoutToken") String checkoutToken
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select o
    from Order o
    join fetch o.user
    where o.id = :orderId
    """)
    Optional<Order> findByIdForUpdate(
            @Param("orderId") Long orderId
    );

    @Query("""
        select order.id
        from Order order
        where order.status = :status
          and order.updatedAt < :staleBefore
        order by order.updatedAt, order.id
    """)
    List<Long> findStaleOrderIds(
            @Param("status") OrderStatus status,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable
    );
}
