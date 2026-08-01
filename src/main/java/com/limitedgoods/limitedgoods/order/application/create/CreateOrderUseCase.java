package com.limitedgoods.limitedgoods.order.application.create;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.order.application.create.dto.OrderAdmissionClaim;
import com.limitedgoods.limitedgoods.order.application.create.idempotency.OrderRequestFingerprintGenerator;
import com.limitedgoods.limitedgoods.order.dto.request.OrderRequest;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.metrics.OrderMetrics;
import com.limitedgoods.limitedgoods.order.policy.OrderProductValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private static final long ORDER_EXPIRED_SECONDS = 300L;

    private final OrderCreatePreconditionChecker preconditionChecker;
    private final OrderAdmissionCoordinator admissionCoordinator;
    private final OrderRequestFingerprintGenerator fingerprintGenerator;
    private final OrderCreateTransactionService orderCreateTransactionService;
    private final OrderMetrics orderMetrics;


    public OrderResponse execute(Long userId, OrderRequest request) {
        Optional<OrderAdmissionClaim> admissionClaim = Optional.empty();

        try{
            preconditionChecker.validateRequest(request);

            String checkoutToken = request.checkoutToken();
            String requestFingerprint = fingerprintGenerator.generate(request.items());

            OrderResponse existing =
                    orderCreateTransactionService.findIdempotentOrder(
                        userId,
                        checkoutToken,
                        requestFingerprint
            );

            if (existing != null) {
                orderMetrics.recordOrderCreate("duplicate", "idempotent_replay");
                return existing;
            }

            OrderProductValidationResult validationResult = preconditionChecker.checkNewOrder(userId, request);

            admissionClaim =
                    admissionCoordinator.claimAdmissionIfRequired(
                            request.admissionToken(),
                            userId,
                            validationResult.admissionProductId(),
                            checkoutToken,
                            requestFingerprint
                    );


            OrderResponse order =
                    orderCreateTransactionService.createOrder(
                            userId,
                            request.items(),
                            ORDER_EXPIRED_SECONDS,
                            checkoutToken,
                            requestFingerprint
                    );

            admissionCoordinator.completeClaimAfterOrderCreated(admissionClaim);

            return order;

        } catch (BusinessException exception) {
            orderMetrics.recordOrderCreate("failure", exception.getErrorCode().getCode());

            admissionCoordinator.releaseClaimAfterBusinessFailure(admissionClaim);

            throw exception;

        } catch (RuntimeException exception) {
            orderMetrics.recordOrderCreate("failure", "internal_error");

            admissionCoordinator.retainClaimAfterUnknownFailure(admissionClaim, exception);

            throw exception;
        }
    }
}
