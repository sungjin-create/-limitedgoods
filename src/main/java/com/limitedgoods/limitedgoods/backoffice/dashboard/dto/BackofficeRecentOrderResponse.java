package com.limitedgoods.limitedgoods.backoffice.dashboard.dto;

import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class BackofficeRecentOrderResponse {
    private Long id;
    private String customerEmail;
    private String productName;
    private long itemCount;
    private long amount;
    private OrderStatus status;
    private LocalDateTime createdAt;
}
