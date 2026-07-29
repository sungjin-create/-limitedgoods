package com.limitedgoods.limitedgoods.backoffice.order.controller;

import com.limitedgoods.limitedgoods.backoffice.order.dto.OrderStatusResponse;
import com.limitedgoods.limitedgoods.backoffice.order.dto.OrdersResponse;
import com.limitedgoods.limitedgoods.backoffice.order.service.AdminRefundService;
import com.limitedgoods.limitedgoods.backoffice.order.service.BackofficeOrderService;
import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/backoffice/order")
@RequiredArgsConstructor
public class BackofficeOrderController {

    private final BackofficeOrderService backofficeOrderService;
    private final AdminRefundService adminRefundService;

    @GetMapping
    public ResponseEntity<ApiResponse<OrdersResponse>> findBackofficeOrders(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startAt,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endAt
    ){
        return ResponseEntity.ok(ApiResponse.success(
                backofficeOrderService.findBackofficeOrders(startAt, endAt)));
    }

    @PatchMapping("/{orderId}/complete")
    public ResponseEntity<ApiResponse<OrderStatusResponse>> completeOrder(
            @PathVariable("orderId") Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        return ResponseEntity.ok(ApiResponse.success(
                backofficeOrderService.completeOrder(userDetails.getUserId(), orderId)));
    }

    @PostMapping("/{orderId}/refund/reconcile")
    public ResponseEntity<ApiResponse<OrderResponse>> reconcileRefund(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                        adminRefundService.reconcile(userDetails.getUserId(),orderId)));
    }

    @PostMapping("/{orderId}/refund/retry")
    public ResponseEntity<ApiResponse<OrderResponse>> retryRefund(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                        adminRefundService.retry(userDetails.getUserId(),orderId)));
    }

}
