package com.limitedgoods.limitedgoods.order.controller;

import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import com.limitedgoods.limitedgoods.order.application.cancel.CancelOrderUseCase;
import com.limitedgoods.limitedgoods.order.application.create.CreateOrderUseCase;
import com.limitedgoods.limitedgoods.order.application.payment.PayOrderUseCase;
import com.limitedgoods.limitedgoods.order.application.query.OrderQueryService;
import com.limitedgoods.limitedgoods.order.dto.request.OrderRequest;
import com.limitedgoods.limitedgoods.order.dto.response.OrderDetailResponse;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.dto.response.OrderSummaryResponse;
import com.limitedgoods.limitedgoods.payment.dto.PaymentRequest;
import com.limitedgoods.limitedgoods.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/order")
@RequiredArgsConstructor

public class OrderController {

    private final OrderQueryService orderQueryService;
    private final CreateOrderUseCase createOrderUseCase;
    private final PayOrderUseCase payOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest orderRequest,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        OrderResponse response = createOrderUseCase.execute(customUserDetails.getUserId(), orderRequest);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<ApiResponse<OrderResponse>> payOrder(
            @PathVariable Long orderId,
            @RequestBody PaymentRequest paymentRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        OrderResponse response = payOrderUseCase.execute(
                customUserDetails.getUserId(),
                orderId,
                paymentRequest,
                idempotencyKey
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> findMyOrders(
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        List<OrderSummaryResponse> response = orderQueryService.findMyOrders(
                customUserDetails.getUserId()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> findOrderDetail(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        OrderDetailResponse response = orderQueryService.findOrderDetail(
                customUserDetails.getUserId(),
                orderId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        OrderResponse response = cancelOrderUseCase.execute(userDetails.getUserId(),orderId);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @PostMapping("/{orderId}/refund/retry")
    public ResponseEntity<ApiResponse<OrderResponse>> retryRefund(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        OrderResponse response = cancelOrderUseCase.execute(userDetails.getUserId(), orderId);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

}
