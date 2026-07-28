package com.limitedgoods.limitedgoods.backoffice.analytics.controller;

import com.limitedgoods.limitedgoods.backoffice.analytics.dto.AnalyticsOverviewResponse;
import com.limitedgoods.limitedgoods.backoffice.analytics.dto.ProductSalesResponse;
import com.limitedgoods.limitedgoods.backoffice.analytics.service.BackofficeAnalyticsQueryService;
import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/backoffice/analytics")
@RequiredArgsConstructor
public class BackofficeAnalyticsController {

    private final BackofficeAnalyticsQueryService analyticsQueryService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> getOverview(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(analyticsQueryService.getOverview(from, to)));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductSalesResponse>>> getTopProducts(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsQueryService.getTopProducts(from, to, limit)
        ));
    }
}
