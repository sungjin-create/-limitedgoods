package com.limitedgoods.limitedgoods.order.purchase.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class UserProductPurchaseCounterRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public int tryReserve(Long userId, Long productId, int quantity, int purchaseLimit) {
        String sql = """
            INSERT INTO user_product_purchase_counter (
                user_id,
                product_id,
                reserved_quantity,
                paid_quantity,
                purchase_limit,
                updated_at
            )
            VALUES (
                :userId,
                :productId,
                :quantity,
                0,
                :purchaseLimit,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (user_id, product_id)
            DO UPDATE
               SET reserved_quantity =
                       user_product_purchase_counter.reserved_quantity
                       + EXCLUDED.reserved_quantity,
                   purchase_limit = EXCLUDED.purchase_limit,
                   updated_at = CURRENT_TIMESTAMP
             WHERE user_product_purchase_counter.reserved_quantity
                   + user_product_purchase_counter.paid_quantity
                   + EXCLUDED.reserved_quantity
                   <= EXCLUDED.purchase_limit
            """;

        return jdbcTemplate.update(sql, Map.of(
                "userId", userId,
                "productId", productId,
                "quantity", quantity,
                "purchaseLimit", purchaseLimit
        ));
    }

    public int releaseReservation(Long userId, Long productId, int quantity) {
        String sql = """
            UPDATE user_product_purchase_counter
               SET reserved_quantity = reserved_quantity - :quantity,
                   updated_at = CURRENT_TIMESTAMP
             WHERE user_id = :userId
               AND product_id = :productId
               AND reserved_quantity >= :quantity
            """;

        return jdbcTemplate.update(sql, Map.of(
                "userId", userId,
                "productId", productId,
                "quantity", quantity
        ));
    }

    public int confirmPayment(Long userId, Long productId, int quantity) {
        String sql = """
            UPDATE user_product_purchase_counter
               SET reserved_quantity = reserved_quantity - :quantity,
                   paid_quantity = paid_quantity + :quantity,
                   updated_at = CURRENT_TIMESTAMP
             WHERE user_id = :userId
               AND product_id = :productId
               AND reserved_quantity >= :quantity
            """;

        return jdbcTemplate.update(sql, Map.of(
                "userId", userId,
                "productId", productId,
                "quantity", quantity
        ));
    }

    public int releasePaidQuantity(Long userId, Long productId, int quantity) {
        String sql = """
            UPDATE user_product_purchase_counter
               SET paid_quantity = paid_quantity - :quantity,
                   updated_at = CURRENT_TIMESTAMP
             WHERE user_id = :userId
               AND product_id = :productId
               AND paid_quantity >= :quantity
            """;

        return jdbcTemplate.update(sql, Map.of(
                "userId", userId,
                "productId", productId,
                "quantity", quantity
        ));
    }
}