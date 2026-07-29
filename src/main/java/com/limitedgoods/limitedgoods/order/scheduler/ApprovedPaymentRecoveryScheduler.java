package com.limitedgoods.limitedgoods.order.scheduler;

import com.limitedgoods.limitedgoods.order.application.payment.ApprovedPaymentFinalizer;
import com.limitedgoods.limitedgoods.order.application.support.OrderAccessService;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovedPaymentRecoveryScheduler {

    private final OrderRepository orderRepository;
    private final ApprovedPaymentFinalizer paymentFinalizer;
    private final OrderAccessService orderAccessService;

    @Scheduled(fixedDelayString = "${payment.finalize.delay-ms:30000}")
    public void recoverApprovedPayments() {
        List<Long> orderIds = orderRepository.findStaleOrderIds(
                OrderStatus.PAYMENT_APPROVED,
                LocalDateTime.now().minusMinutes(1),
                Pageable.ofSize(100)
        );

        for (Long orderId : orderIds) {
            try {
                Order order = orderAccessService.getOrderForUpdate(orderId);

                paymentFinalizer.finalizePayment(
                        order.getUser().getId(),
                        orderId
                );
            } catch (Exception exception) {
                log.error(
                        "PAYMENT_APPROVED 주문 복구 실패. orderId={}",
                        orderId,
                        exception
                );
            }
        }
    }
}