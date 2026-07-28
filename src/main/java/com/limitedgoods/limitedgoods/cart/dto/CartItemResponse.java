package com.limitedgoods.limitedgoods.cart.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Builder
public record CartItemResponse (
         Long id,
         Long productId,
         String productName,
         int quantity,
         int price,
         long totalPrice,
         LocalDateTime createdAt,
         LocalDateTime updatedAt
){

}
