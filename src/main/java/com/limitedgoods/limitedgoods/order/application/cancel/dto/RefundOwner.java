package com.limitedgoods.limitedgoods.order.application.cancel.dto;

public record RefundOwner(
        Long userId,
        Long orderId
) {
}