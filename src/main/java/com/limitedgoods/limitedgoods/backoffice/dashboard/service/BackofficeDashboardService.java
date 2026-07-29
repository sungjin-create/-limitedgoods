package com.limitedgoods.limitedgoods.backoffice.dashboard.service;

import com.limitedgoods.limitedgoods.backoffice.dashboard.dto.*;
import com.limitedgoods.limitedgoods.backoffice.dashboard.repository.BackofficeDashboardQueryRepository;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.repository.OrderRepository;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BackofficeDashboardService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final BackofficeDashboardQueryRepository backofficeDashboardQueryRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public BackofficeResponse getDashboard() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime yesterdayEnd = todayStart;

        List<OrderStatus> revenueStatuses = List.of(
                OrderStatus.PAID,
                OrderStatus.COMPLETED
        );

        long todayOrderCount = orderRepository.countCreatedBetween(todayStart, tomorrowStart);
        long yesterdayOrderCount = orderRepository.countCreatedBetween(yesterdayStart, yesterdayEnd);

        long todayPaidOrderCount = orderRepository.countPaidBetween(todayStart, tomorrowStart, revenueStatuses);
        long todayRevenue = orderRepository.sumRevenueBetween(todayStart, tomorrowStart, revenueStatuses);

        long yesterdayRevenue = orderRepository.sumRevenueBetween(
                yesterdayStart,
                yesterdayEnd,
                revenueStatuses
        );

        long pendingCount = orderRepository.countCreatedBetweenAndStatusIn(
                todayStart,
                tomorrowStart,
                List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_APPROVED)
        );

        long lowStockProductCount = productRepository.countLowStockProducts(LOW_STOCK_THRESHOLD);
        long soldOutProductCount = productRepository.countSoldOutProducts();

        BackofficeSummaryResponse summary = BackofficeSummaryResponse.builder()
                .todayOrderCount(todayOrderCount)
                .orderGrowthRate(calculateGrowthRate(todayOrderCount, yesterdayOrderCount))
                .todayRevenue(todayRevenue)
                .revenueGrowthRate(calculateGrowthRate(todayRevenue, yesterdayRevenue))
                .todayPaidOrderCount(todayPaidOrderCount)
                .paymentCompletionRate(calculateRate(todayPaidOrderCount, todayOrderCount))
                .lowStockProductCount(lowStockProductCount)
                .soldOutProductCount(soldOutProductCount)
                .build();


        long paymentFailedCount =
                orderRepository.countCreatedBetweenAndStatusIn(
                        todayStart,
                        tomorrowStart,
                        List.of(OrderStatus.PAYMENT_FAILED)
                );

        long expiredCount =
                orderRepository.countCreatedBetweenAndStatusIn(
                        todayStart,
                        tomorrowStart,
                        List.of(OrderStatus.EXPIRED)
                );

        long refundRequestedCount =
                orderRepository.countCreatedBetweenAndStatusIn(
                        todayStart,
                        tomorrowStart,
                        List.of(OrderStatus.CANCEL_REQUESTED)
                );

        long refundedCount =
                orderRepository.countCreatedBetweenAndStatusIn(
                        todayStart,
                        tomorrowStart,
                        List.of(OrderStatus.REFUNDED)
                );

        long refundFailedCount =
                orderRepository.countCreatedBetweenAndStatusIn(
                        todayStart,
                        tomorrowStart,
                        List.of(OrderStatus.CANCEL_FAILED)
                );


        BackofficeOrderFlowResponse orderFlow =
                BackofficeOrderFlowResponse.builder()
                        .createdCount(todayOrderCount)
                        .paidCount(todayPaidOrderCount)
                        .pendingCount(pendingCount)
                        .paymentFailedCount(paymentFailedCount)
                        .expiredCount(expiredCount)
                        .refundRequestedCount(refundRequestedCount)
                        .refundedCount(refundedCount)
                        .refundFailedCount(refundFailedCount)
                        .build();

        List<BackofficeRecentOrderResponse> recentOrders =
                backofficeDashboardQueryRepository.findRecentOrders(PageRequest.of(0, 5));

        List<BackofficeAlertResponse> alerts = buildAlerts(
                lowStockProductCount,
                soldOutProductCount,
                pendingCount,
                paymentFailedCount,
                expiredCount,
                refundFailedCount
        );

        return BackofficeResponse.builder()
                .summary(summary)
                .orderFlow(orderFlow)
                .recentOrders(recentOrders)
                .alerts(alerts)
                .build();
    }

    private double calculateRate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }

        return Math.round((numerator * 1000.0 / denominator)) / 10.0;
    }

    private double calculateGrowthRate(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }

        return Math.round(((current - previous) * 1000.0 / previous)) / 10.0;
    }

    private List<BackofficeAlertResponse> buildAlerts(
            long lowStockProductCount,
            long soldOutProductCount,
            long pendingCount,
            long paymentFailedCount,
            long expiredCount,
            long refundFailedCount
    ) {
        List<BackofficeAlertResponse> alerts = new java.util.ArrayList<>();

        if (soldOutProductCount > 0) {
            alerts.add(BackofficeAlertResponse.builder()
                    .level("critical")
                    .title("품절 상품 " + soldOutProductCount + "개")
                    .detail("재고가 0 이하인 상품이 있습니다.")
                    .time("방금")
                    .build());
        }

        if (lowStockProductCount > 0) {
            alerts.add(BackofficeAlertResponse.builder()
                    .level("warning")
                    .title("재고 부족 상품 " + lowStockProductCount + "개")
                    .detail("재고가 " + LOW_STOCK_THRESHOLD + "개 이하인 상품을 확인해 주세요.")
                    .time("방금")
                    .build());
        }

        if (pendingCount > 0) {
            alerts.add(BackofficeAlertResponse.builder()
                    .level("info")
                    .title("결제 대기 주문 " + pendingCount + "건")
                    .detail("오늘 생성됐지만 아직 결제가 끝나지 않은 주문입니다.")
                    .time("방금")
                    .build());
        }

        if (paymentFailedCount > 0) {
            alerts.add(BackofficeAlertResponse.builder()
                    .level("warning")
                    .title("결제 실패 주문 " + paymentFailedCount + "건")
                    .detail("오늘 생성된 주문 중 결제가 실패한 주문입니다.")
                    .time("방금")
                    .build());
        }

        if (expiredCount > 0) {
            alerts.add(BackofficeAlertResponse.builder()
                    .level("warning")
                    .title("만료 주문 " + expiredCount + "건")
                    .detail("결제 기한을 초과하여 만료된 주문입니다.")
                    .time("방금")
                    .build());
        }

        if (refundFailedCount > 0) {
            alerts.add(BackofficeAlertResponse.builder()
                    .level("critical")
                    .title("환불 실패 주문 " + refundFailedCount + "건")
                    .detail("관리자의 재조회 또는 재처리가 필요합니다.")
                    .time("방금")
                    .build());
        }

        return alerts;
    }
}