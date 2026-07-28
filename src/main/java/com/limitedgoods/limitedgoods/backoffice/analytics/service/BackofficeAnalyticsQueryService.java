package com.limitedgoods.limitedgoods.backoffice.analytics.service;

import com.limitedgoods.limitedgoods.analytics.entity.DailyOrderFunnelProjection;
import com.limitedgoods.limitedgoods.analytics.entity.DailySalesProjection;
import com.limitedgoods.limitedgoods.analytics.repository.DailyOrderFunnelProjectionRepository;
import com.limitedgoods.limitedgoods.analytics.repository.DailySalesProjectionRepository;
import com.limitedgoods.limitedgoods.analytics.repository.ProductSalesProjectionRepository;
import com.limitedgoods.limitedgoods.backoffice.analytics.dto.AnalyticsOverviewResponse;
import com.limitedgoods.limitedgoods.backoffice.analytics.dto.DailyOrderFunnelResponse;
import com.limitedgoods.limitedgoods.backoffice.analytics.dto.DailySalesResponse;
import com.limitedgoods.limitedgoods.backoffice.analytics.dto.ProductSalesResponse;
import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackofficeAnalyticsQueryService {

    private static final long MAX_RANGE_DAYS = 366;
    private static final int MAX_PRODUCT_LIMIT = 100;

    private final DailySalesProjectionRepository dailySalesRepository;
    private final DailyOrderFunnelProjectionRepository orderFunnelRepository;
    private final ProductSalesProjectionRepository productSalesRepository;

    public AnalyticsOverviewResponse getOverview(LocalDate from, LocalDate to) {
        validateDateRange(from, to);

        List<DailySalesProjection> salesProjections =
                dailySalesRepository
                        .findAllBySalesDateBetweenOrderBySalesDateAsc(from, to);

        List<DailyOrderFunnelProjection> funnelProjections =
                orderFunnelRepository
                        .findAllByOrderDateBetweenOrderByOrderDateAsc(from, to);

        AnalyticsOverviewResponse.SalesSummary salesSummary = buildSalesSummary(salesProjections);

        AnalyticsOverviewResponse.FunnelSummary funnelSummary = buildFunnelSummary(funnelProjections);

        return new AnalyticsOverviewResponse(
                from,
                to,
                salesSummary,
                funnelSummary,
                fillDailySales(from, to, salesProjections),
                fillDailyFunnel(from, to, funnelProjections)
        );
    }

    public List<ProductSalesResponse> getTopProducts(int limit) {
        if (limit < 1 || limit > MAX_PRODUCT_LIMIT) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "limit은 1 이상 100 이하여야 합니다."
            );
        }

        return productSalesRepository
                .findTopSellingProducts(limit)
                .stream()
                .map(view ->
                        new ProductSalesResponse(
                                view.getProductId(),
                                view.getProductName(),
                                view.getPaidOrderCount(),
                                view.getSoldQuantity(),
                                view.getRefundedQuantity(),
                                view.getSoldQuantity() - view.getRefundedQuantity(),
                                view.getGrossRevenue(),
                                view.getRefundAmount(),
                                view.getGrossRevenue() - view.getRefundAmount(),
                                view.getLastSoldAt()
                        )
                )
                .toList();
    }

    private AnalyticsOverviewResponse.SalesSummary buildSalesSummary(
            List<DailySalesProjection> projections
    ) {
        long paidOrderCount = projections.stream()
                .mapToLong(DailySalesProjection::getPaidOrderCount)
                .sum();

        long grossRevenue = projections.stream()
                .mapToLong(DailySalesProjection::getGrossRevenue)
                .sum();

        long refundedOrderCount = projections.stream()
                .mapToLong(DailySalesProjection::getRefundedOrderCount)
                .sum();

        long refundAmount = projections.stream()
                .mapToLong(DailySalesProjection::getRefundAmount)
                .sum();

        long soldQuantity = projections.stream()
                .mapToLong(DailySalesProjection::getSoldQuantity)
                .sum();

        double averageOrderAmount =
                paidOrderCount == 0
                        ? 0
                        : roundOneDecimal((double) grossRevenue/ paidOrderCount);

        return new AnalyticsOverviewResponse.SalesSummary(
                paidOrderCount,
                grossRevenue,
                refundedOrderCount,
                refundAmount,
                grossRevenue - refundAmount,
                soldQuantity,
                averageOrderAmount
        );
    }

    private AnalyticsOverviewResponse.FunnelSummary buildFunnelSummary(
            List<DailyOrderFunnelProjection> projections
    ) {
        long createdCount = projections.stream()
                .mapToLong(DailyOrderFunnelProjection::getCreatedOrderCount)
                .sum();

        long paidCount = projections.stream()
                .mapToLong(DailyOrderFunnelProjection::getPaidOrderCount)
                .sum();

        long failureCount = projections.stream()
                .mapToLong(DailyOrderFunnelProjection::getPaymentFailureCount)
                .sum();

        long expiredCount = projections.stream()
                .mapToLong(DailyOrderFunnelProjection::getExpiredOrderCount)
                .sum();

        long canceledCount = projections.stream()
                .mapToLong(DailyOrderFunnelProjection::getCanceledOrderCount)
                .sum();

        return new AnalyticsOverviewResponse.FunnelSummary(
                createdCount,
                paidCount,
                failureCount,
                expiredCount,
                canceledCount,
                calculateRate(paidCount, createdCount),
                calculateRate(expiredCount, createdCount),
                calculateRate(canceledCount, paidCount)
        );
    }

    private List<DailySalesResponse> fillDailySales(
            LocalDate from,
            LocalDate to,
            List<DailySalesProjection> projections
    ) {
        Map<LocalDate, DailySalesProjection> projectionByDate =
                projections.stream()
                        .collect(Collectors.toMap(
                                DailySalesProjection::getSalesDate,
                                Function.identity()
                        ));

        return from
                .datesUntil(to.plusDays(1))
                .map(date -> {
                    DailySalesProjection projection = projectionByDate.get(date);

                    return projection == null
                            ? DailySalesResponse.empty(date)
                            : DailySalesResponse.from(projection);
                })
                .toList();
    }

    private List<DailyOrderFunnelResponse> fillDailyFunnel(
            LocalDate from,
            LocalDate to,
            List<DailyOrderFunnelProjection> projections
    ) {
        Map<LocalDate, DailyOrderFunnelProjection> projectionByDate =
                projections.stream()
                        .collect(Collectors.toMap(
                                DailyOrderFunnelProjection::getOrderDate,
                                Function.identity()
                        ));

        return from
                .datesUntil(to.plusDays(1))
                .map(date -> {
                    DailyOrderFunnelProjection projection =
                            projectionByDate.get(date);

                    return projection == null
                            ? DailyOrderFunnelResponse.empty(date)
                            : DailyOrderFunnelResponse.from(projection);
                })
                .toList();
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "from과 to는 필수입니다."
            );
        }

        if (from.isAfter(to)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "from은 to보다 이후일 수 없습니다."
            );
        }

        long rangeDays = ChronoUnit.DAYS.between(from, to) + 1;

        if (rangeDays > MAX_RANGE_DAYS) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "조회 기간은 최대 366일입니다."
            );
        }
    }

    private double calculateRate(
            long numerator,
            long denominator
    ) {
        if (denominator == 0) {
            return 0;
        }

        return roundOneDecimal(numerator * 100.0 / denominator);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}