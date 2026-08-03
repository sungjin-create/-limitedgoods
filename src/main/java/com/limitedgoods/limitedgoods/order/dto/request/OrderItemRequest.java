package com.limitedgoods.limitedgoods.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;


@Builder
public record OrderItemRequest(

    @NotNull(message = "상품ID는 필수입니다.")
    @Positive(message = "ID는 양수만 가능합니다.")
    Long productId,

    @NotNull(message = "수량은 필수입니다.")
    @Positive(message = "수량은 양수만 가능합니다.")
    Integer quantity
){

}
