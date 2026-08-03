package com.limitedgoods.limitedgoods.order.application.create;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.create.dto.OrderAdmissionClaim;
import com.limitedgoods.limitedgoods.order.application.create.idempotency.OrderRequestFingerprintGenerator;
import com.limitedgoods.limitedgoods.order.application.history.OrderStatusHistoryService;
import com.limitedgoods.limitedgoods.order.dto.request.OrderItemRequest;
import com.limitedgoods.limitedgoods.order.dto.request.OrderRequest;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.policy.OrderProductValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final String CHECKOUT_TOKEN = "checkout-token";
    private static final String FINGERPRINT = "request-fingerprint";
    private static final long GENERATION = 3L;

    @Mock OrderCreatePreconditionChecker preconditionChecker;
    @Mock OrderAdmissionCoordinator admissionCoordinator;
    @Mock OrderRequestFingerprintGenerator fingerprintGenerator;
    @Mock OrderCreateTransactionService transactionService;
    private CreateOrderUseCase useCase;
    private OrderRequest request;

    @BeforeEach
    void setUp() {
        useCase = new CreateOrderUseCase(
                preconditionChecker,
                admissionCoordinator,
                fingerprintGenerator,
                transactionService
        );
        request = request(CHECKOUT_TOKEN, PRODUCT_ID, 1, "admission-token");
    }

    @Test
    @DisplayName("동일 checkoutToken과 요청이면 기존 주문을 반환하고 신규 주문 절차를 건너뛴다")
    void execute_idempotentRequest_returnsExistingOrder() {
        OrderResponse existing = response(100L);
        when(fingerprintGenerator.generate(request.items())).thenReturn(FINGERPRINT);
        when(transactionService.findIdempotentOrder(USER_ID, CHECKOUT_TOKEN, FINGERPRINT))
                .thenReturn(existing);

        OrderResponse result = useCase.execute(USER_ID, request);

        assertThat(result).isSameAs(existing);
        verify(preconditionChecker).validateRequest(request);
        verify(preconditionChecker, never()).checkNewOrder(anyLong(), any());
        verifyNoInteractions(admissionCoordinator);
        verify(transactionService, never()).createOrder(anyLong(), anyList(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("신규 한정 상품 주문은 입장 토큰을 선점하고 주문 생성 후 소비를 완료한다")
    void execute_newLimitedOrder_claimsAndCompletesAdmission() {
        OrderProductValidationResult validation = new OrderProductValidationResult(PRODUCT_ID);
        OrderAdmissionClaim claim = new OrderAdmissionClaim(
                "admission-token", USER_ID, PRODUCT_ID, "claim-id", GENERATION
        );
        OrderResponse created = response(101L);
        stubNewOrder(validation, Optional.of(claim));
        when(transactionService.createOrder(
                USER_ID, request.items(), 300L, CHECKOUT_TOKEN, FINGERPRINT
        )).thenReturn(created);

        OrderResponse result = useCase.execute(USER_ID, request);

        assertThat(result).isSameAs(created);
        InOrder inOrder = inOrder(preconditionChecker, admissionCoordinator, transactionService);
        inOrder.verify(preconditionChecker).validateRequest(request);
        inOrder.verify(transactionService).findIdempotentOrder(USER_ID, CHECKOUT_TOKEN, FINGERPRINT);
        inOrder.verify(preconditionChecker).checkNewOrder(USER_ID, request);
        inOrder.verify(admissionCoordinator).claimAdmissionIfRequired(
                "admission-token", USER_ID, PRODUCT_ID, CHECKOUT_TOKEN, FINGERPRINT
        );
        inOrder.verify(transactionService).createOrder(
                USER_ID, request.items(), 300L, CHECKOUT_TOKEN, FINGERPRINT
        );
        inOrder.verify(admissionCoordinator).completeClaimAfterOrderCreated(Optional.of(claim));
    }

    @Test
    @DisplayName("재고 부족 같은 비즈니스 실패이면 선점한 입장 토큰을 해제한다")
    void execute_businessFailure_releasesAdmissionClaim() {
        OrderAdmissionClaim claim = new OrderAdmissionClaim(
                "admission-token", USER_ID, PRODUCT_ID, "claim-id", GENERATION
        );
        stubNewOrder(new OrderProductValidationResult(PRODUCT_ID), Optional.of(claim));
        BusinessException failure = new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        when(transactionService.createOrder(anyLong(), anyList(), anyLong(), anyString(), anyString()))
                .thenThrow(failure);

        assertThatThrownBy(() -> useCase.execute(USER_ID, request)).isSameAs(failure);

        verify(admissionCoordinator).releaseClaimAfterBusinessFailure(Optional.of(claim));
        verify(admissionCoordinator, never()).completeClaimAfterOrderCreated(any());
        verify(admissionCoordinator, never()).retainClaimAfterUnknownFailure(any(), any());
    }

    @Test
    @DisplayName("DB 결과가 불명확한 시스템 실패이면 입장 토큰 선점을 유지한다")
    void execute_unknownFailure_retainsAdmissionClaim() {
        OrderAdmissionClaim claim = new OrderAdmissionClaim(
                "admission-token", USER_ID, PRODUCT_ID, "claim-id", GENERATION
        );
        stubNewOrder(new OrderProductValidationResult(PRODUCT_ID), Optional.of(claim));
        RuntimeException failure = new RuntimeException("connection lost after commit");
        when(transactionService.createOrder(anyLong(), anyList(), anyLong(), anyString(), anyString()))
                .thenThrow(failure);

        assertThatThrownBy(() -> useCase.execute(USER_ID, request)).isSameAs(failure);

        verify(admissionCoordinator).retainClaimAfterUnknownFailure(Optional.of(claim), failure);
        verify(admissionCoordinator, never()).releaseClaimAfterBusinessFailure(any());
        verify(admissionCoordinator, never()).completeClaimAfterOrderCreated(any());
    }

    private void stubNewOrder(
            OrderProductValidationResult validation,
            Optional<OrderAdmissionClaim> claim
    ) {
        when(fingerprintGenerator.generate(request.items())).thenReturn(FINGERPRINT);
        when(transactionService.findIdempotentOrder(USER_ID, CHECKOUT_TOKEN, FINGERPRINT))
                .thenReturn(null);
        when(preconditionChecker.checkNewOrder(USER_ID, request)).thenReturn(validation);
        when(admissionCoordinator.claimAdmissionIfRequired(
                request.admissionToken(), USER_ID, validation.admissionProductId(),
                CHECKOUT_TOKEN, FINGERPRINT
        )).thenReturn(claim);
    }

    private OrderRequest request(
            String checkoutToken,
            Long productId,
            int quantity,
            String admissionToken
    ) {
        return new OrderRequest(
                checkoutToken,
                List.of(new OrderItemRequest(productId, quantity)),
                admissionToken
        );
    }

    private OrderResponse response(Long orderId) {
        return OrderResponse.builder().id(orderId).userId(USER_ID).build();
    }
}
