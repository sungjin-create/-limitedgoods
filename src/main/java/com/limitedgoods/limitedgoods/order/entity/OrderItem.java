package com.limitedgoods.limitedgoods.order.entity;

import com.limitedgoods.limitedgoods.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "order_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_order_items_order_product",
                columnNames = {
                        "order_id",
                        "product_id"
                }
        )
)
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private int quantity;

    private int price;

    @Column(name = "line_total_price")
    private long lineTotalPrice;

    @Column(name = "purchase_limit_at_order")
    private Integer purchaseLimitAtOrder;
}
