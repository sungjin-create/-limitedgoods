package com.limitedgoods.limitedgoods.order.application.create;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.dto.request.OrderItemRequest;
import com.limitedgoods.limitedgoods.order.dto.request.OrderRequest;
import com.limitedgoods.limitedgoods.order.infrastructure.ratelimit.OrderRateLimiter;
import com.limitedgoods.limitedgoods.order.policy.OrderProductValidationResult;
import com.limitedgoods.limitedgoods.order.policy.ProductOrderPolicy;
import com.limitedgoods.limitedgoods.product.service.ProductSoldOutCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCreatePreconditionCheckerTest {

    @Mock OrderRateLimiter orderRateLimiter;
    @Mock ProductSoldOutCacheService soldOutCacheService;
    @Mock ProductOrderPolicy productOrderPolicy;

    private OrderCreatePreconditionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new OrderCreatePreconditionChecker(
                orderRateLimiter,
                soldOutCacheService,
                productOrderPolicy
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("잘못된 주문 요청을 저장 절차 전에 거절한다")
    void validateRequest_invalidRequest_throws(
            String description,
            OrderRequest request,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(() -> checker.validateRequest(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expectedErrorCode);
    }

    @Test
    @DisplayName("한 상품이라도 요청 제한에 걸리면 이후 검증을 중단한다")
    void checkNewOrder_rateLimited_throws() {
        OrderRequest request = request(
                new OrderItemRequest(10L, 1),
                new OrderItemRequest(20L, 1)
        );
        when(orderRateLimiter.allow(1L, 10L)).thenReturn(true);
        when(orderRateLimiter.allow(1L, 20L)).thenReturn(false);

        assertBusinessError(
                () -> checker.checkNewOrder(1L, request),
                ErrorCode.TOO_MANY_ORDER_REQUESTS
        );

        verifyNoInteractions(soldOutCacheService, productOrderPolicy);
    }

    @Test
    @DisplayName("품절 캐시가 감지되면 DB 상품 정책 검증을 수행하지 않는다")
    void checkNewOrder_soldOutCacheHit_throws() {
        OrderRequest request = request(new OrderItemRequest(10L, 1));
        when(orderRateLimiter.allow(1L, 10L)).thenReturn(true);
        when(soldOutCacheService.isSoldOut(10L)).thenReturn(true);

        assertBusinessError(
                () -> checker.checkNewOrder(1L, request),
                ErrorCode.INSUFFICIENT_STOCK
        );

        verifyNoInteractions(productOrderPolicy);
    }

    @Test
    @DisplayName("사전 검사 통과 시 DB 기반 상품 정책 결과를 반환한다")
    void checkNewOrder_validRequest_returnsPolicyResult() {
        OrderRequest request = request(new OrderItemRequest(10L, 1));
        OrderProductValidationResult expected = new OrderProductValidationResult(10L);
        when(orderRateLimiter.allow(1L, 10L)).thenReturn(true);
        when(soldOutCacheService.isSoldOut(10L)).thenReturn(false);
        when(productOrderPolicy.validate(request.items())).thenReturn(expected);

        org.assertj.core.api.Assertions.assertThat(checker.checkNewOrder(1L, request))
                .isSameAs(expected);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidRequests() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "요청 null", null, ErrorCode.INVALID_INPUT
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "checkout token 없음",
                        new OrderRequest(" ", List.of(new OrderItemRequest(1L, 1)), null),
                        ErrorCode.HAS_NO_CHECKOUT_TOKEN
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "상품 목록 없음",
                        new OrderRequest("token", List.of(), null),
                        ErrorCode.INVALID_INPUT
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "상품 ID 없음",
                        new OrderRequest("token", List.of(new OrderItemRequest(null, 1)), null),
                        ErrorCode.INVALID_INPUT
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "수량 0",
                        new OrderRequest("token", List.of(new OrderItemRequest(1L, 0)), null),
                        ErrorCode.INVALID_INPUT
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "중복 상품",
                        new OrderRequest("token", List.of(
                                new OrderItemRequest(1L, 1),
                                new OrderItemRequest(1L, 2)
                        ), null),
                        ErrorCode.DUPLICATE_ORDER_PRODUCT
                )
        );
    }

    private OrderRequest request(OrderItemRequest... items) {
        return new OrderRequest("token", List.of(items), null);
    }

    private void assertBusinessError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
