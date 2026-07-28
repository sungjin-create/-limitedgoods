package com.limitedgoods.limitedgoods.cart.repository;

import com.limitedgoods.limitedgoods.cart.entity.Cart;
import com.limitedgoods.limitedgoods.cart.entity.CartItem;
import com.limitedgoods.limitedgoods.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findCartItemByCart(Cart cart);

    boolean existsByCartIdAndProductId(Long cartId, Long product);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    delete from CartItem ci
    where ci.cart.user.id = :userId
      and ci.product.id in :productIds
    """)
    int deleteByUserIdAndProductIdIn(
            @Param("userId") Long userId,
            @Param("productIds") List<Long> productIds
    );

    Optional<CartItem> findByIdAndCartUserId(Long cartItemId, Long userId);

}
