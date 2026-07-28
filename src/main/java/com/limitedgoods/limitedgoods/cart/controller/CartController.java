package com.limitedgoods.limitedgoods.cart.controller;

import com.limitedgoods.limitedgoods.cart.dto.*;
import com.limitedgoods.limitedgoods.cart.service.CartService;
import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import com.limitedgoods.limitedgoods.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> getCart(
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ){
        List<CartItemResponse> cartItemList =  cartService.getCartItemList(customUserDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(cartItemList));
    }

    @PostMapping("/item/add")
    public ResponseEntity<ApiResponse<CartItemResponse>> addToCart(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Valid @RequestBody CartItemRequest cartItemRequest
    ){
        Long userId = customUserDetails.getUserId();
        Long productId = cartItemRequest.productId();
        int quantity = cartItemRequest.quantity();
        CartItemResponse cartItemResponse = cartService.addToCart(userId, productId, quantity);
        return ResponseEntity.ok(ApiResponse.success(cartItemResponse));
    }

    @PostMapping("/item/update")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateCartItem(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestBody CartItemUpdateRequest cartItemUpdateRequest
    ){
        Long userId = customUserDetails.getUserId();
        Long cartItemId = cartItemUpdateRequest.cartItemId();
        int quantity = cartItemUpdateRequest.quantity();
        cartService.updateCartItem(userId, cartItemId, quantity);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/item")
    public ResponseEntity<ApiResponse> deleteCartItem(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam Long cartItemId){
        cartService.deleteCartItem(cartItemId, customUserDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
