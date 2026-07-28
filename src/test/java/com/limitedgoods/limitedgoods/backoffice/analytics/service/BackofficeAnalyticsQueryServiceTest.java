package com.limitedgoods.limitedgoods.backoffice.analytics.service;

import com.limitedgoods.limitedgoods.analytics.repository.DailyOrderFunnelProjectionRepository;
import com.limitedgoods.limitedgoods.analytics.repository.DailySalesProjectionRepository;
import com.limitedgoods.limitedgoods.analytics.repository.ProductSalesProjectionRepository;
import com.limitedgoods.limitedgoods.backoffice.analytics.dto.ProductSalesResponse;
import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackofficeAnalyticsQueryServiceTest {

    @Mock
    private DailySalesProjectionRepository dailySalesRepository;

    @Mock
    private DailyOrderFunnelProjectionRepository orderFunnelRepository;

    @Mock
    private ProductSalesProjectionRepository productSalesRepository;

    @Mock
    private ProductSalesProjectionRepository.ProductSalesRankView rankView;

    @InjectMocks
    private BackofficeAnalyticsQueryService service;

    @Test
    void returnsProductRankingForInclusiveDateRange() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 7);

        when(productSalesRepository.findTopSellingProducts(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(),
                5
        )).thenReturn(List.of(rankView));
        when(rankView.getProductId()).thenReturn(10L);
        when(rankView.getProductName()).thenReturn("한정 상품");
        when(rankView.getPaidOrderCount()).thenReturn(3L);
        when(rankView.getSoldQuantity()).thenReturn(5L);
        when(rankView.getRefundedQuantity()).thenReturn(1L);
        when(rankView.getGrossRevenue()).thenReturn(50_000L);
        when(rankView.getRefundAmount()).thenReturn(10_000L);
        when(rankView.getLastSoldAt()).thenReturn(LocalDateTime.of(2026, 7, 7, 10, 30));

        List<ProductSalesResponse> result = service.getTopProducts(from, to, 5);

        assertThat(result).containsExactly(new ProductSalesResponse(
                10L,
                "한정 상품",
                3L,
                5L,
                1L,
                4L,
                50_000L,
                10_000L,
                40_000L,
                LocalDateTime.of(2026, 7, 7, 10, 30)
        ));
        verify(productSalesRepository).findTopSellingProducts(
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 7, 8, 0, 0),
                5
        );
    }

    @Test
    void rejectsInvalidDateRangeBeforeQueryingRanking() {
        LocalDate from = LocalDate.of(2026, 7, 8);
        LocalDate to = LocalDate.of(2026, 7, 7);

        assertThatThrownBy(() -> service.getTopProducts(from, to, 5))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(productSalesRepository);
    }
}
