package com.limitedgoods.limitedgoods.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;


public record CartItemRequest (
        @NotNull
        Long productId,

        @Positive
        int quantity
) {
}
