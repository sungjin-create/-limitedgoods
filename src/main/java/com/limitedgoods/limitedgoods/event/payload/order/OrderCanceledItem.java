package com.limitedgoods.limitedgoods.event.payload.order;

public record OrderCanceledItem(
        Long productId,
        int quantity,
        int unitPrice
) {
}