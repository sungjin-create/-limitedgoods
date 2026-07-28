package com.limitedgoods.limitedgoods.cart.service;

import com.limitedgoods.limitedgoods.cart.dto.CartItemResponse;
import com.limitedgoods.limitedgoods.cart.entity.Cart;
import com.limitedgoods.limitedgoods.cart.entity.CartItem;
import com.limitedgoods.limitedgoods.cart.repository.CartItemRepository;
import com.limitedgoods.limitedgoods.cart.repository.CartRepository;
import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.user.entity.User;
import com.limitedgoods.limitedgoods.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public List<CartItemResponse> getCartItemList(Long userId){

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> createCart(user));

        return cartItemRepository.findCartItemByCart(cart)
                .stream()
                .map(this::toCartItemResponseDto)
                .toList();
    }

    @Transactional
    public CartItemResponse addToCart(Long userId, Long productId, int quantity){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Cart cart = cartRepository.findByUser(user)
                .orElseGet(()->createCart(user));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PRODUCT_ID));

        if (cartItemRepository.existsByCartIdAndProductId(cart.getId(), product.getId())) {
            throw new BusinessException(ErrorCode.CART_ITEM_ALREADY_ADD);
        }

        if(product.getStock() < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        int price = product.getPrice();
        long totalPrice = Math.multiplyExact((long) price, quantity);

        CartItem cartItem = cartItemRepository.save(
                CartItem.builder()
                        .cart(cart)
                        .product(product)
                        .price(price)
                        .totalPrice(totalPrice)
                        .quantity(quantity)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        return toCartItemResponseDto(cartItem);
    }

    @Transactional
    public void updateCartItem(Long userId, Long cartItemId, int quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        cartRepository.findByUser(user)
                .orElseGet(()->createCart(user));

        CartItem cartItem = cartItemRepository.findByIdAndCartUserId(cartItemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        Product product = productRepository.findById(cartItem.getProduct().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PRODUCT_ID));

        if(product.getStock() < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        int price = product.getPrice();
        long totalPrice = Math.multiplyExact((long)price, quantity);

        cartItem.updateQuantityAndPrice(quantity, price, totalPrice);
    }

    @Transactional
    public void deleteCartItem(Long cartItemId, Long userId){
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));

        cartRepository.findByUser(user)
                .orElseThrow(()-> new BusinessException(ErrorCode.CART_NOT_FOUND));

        cartItemRepository.findByIdAndCartUserId(cartItemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        cartItemRepository.deleteById(cartItemId);

    }

    @Transactional
    public void removeOrderedItemList(
            Long userId,
            List<Long> productIdList
    ) {
        if (productIdList == null || productIdList.isEmpty()) {
            return;
        }

        cartItemRepository.deleteByUserIdAndProductIdIn(userId, productIdList);
    }

    private Cart createCart(User user){

        return cartRepository.save(Cart.builder()
                        .user(user)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
    }

    private CartItemResponse toCartItemResponseDto(CartItem cartItem){

        return CartItemResponse.builder()
                .id(cartItem.getId())
                .productId(cartItem.getProduct().getId())
                .productName(cartItem.getProduct().getName())
                .price(cartItem.getPrice())
                .quantity(cartItem.getQuantity())
                .totalPrice(cartItem.getTotalPrice())
                .createdAt(cartItem.getCreatedAt())
                .updatedAt(cartItem.getUpdatedAt())
                .build();
    }
}
