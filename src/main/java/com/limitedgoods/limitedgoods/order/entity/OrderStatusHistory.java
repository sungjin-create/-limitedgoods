package com.limitedgoods.limitedgoods.order.entity;

import com.limitedgoods.limitedgoods.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "order_status_history")
@NoArgsConstructor
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private OrderStatus toStatus;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private OrderStatusHistory(
            Order order,
            User changedByUser,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String reason
    ) {
        this.order = order;
        this.changedByUser = changedByUser;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public static OrderStatusHistory create(
            Order order,
            User changedByUser,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String reason
    ) {
        return new OrderStatusHistory(
                order,
                changedByUser,
                fromStatus,
                toStatus,
                reason
        );
    }
}
