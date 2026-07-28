package com.limitedgoods.limitedgoods.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

public record CartItemUpdateRequest(
        @NotNull
        Long cartItemId,

        @Positive
        int quantity
) {

}
