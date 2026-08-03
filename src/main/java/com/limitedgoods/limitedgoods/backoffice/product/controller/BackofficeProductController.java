package com.limitedgoods.limitedgoods.backoffice.product.controller;

import com.limitedgoods.limitedgoods.backoffice.product.dto.request.ProductRegisterRequest;
import com.limitedgoods.limitedgoods.backoffice.product.dto.request.ProductSaleSettingsRequest;
import com.limitedgoods.limitedgoods.backoffice.product.dto.request.StockAdjustmentRequest;
import com.limitedgoods.limitedgoods.backoffice.product.dto.response.ProductResponse;
import com.limitedgoods.limitedgoods.backoffice.product.service.BackofficeProductService;
import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/backoffice/product")
@RequiredArgsConstructor
public class BackofficeProductController {

    private final BackofficeProductService backofficeProductService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(
            @PageableDefault(size = 100, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(backofficeProductService.getProducts(pageable)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<ProductResponse>> productRegister(
            @Valid @RequestBody ProductRegisterRequest productRegisterRequest) {
        ProductResponse response = backofficeProductService.registerProduct(productRegisterRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/sale-settings")
    public ResponseEntity<ApiResponse<ProductResponse>> updateSaleSettings(
            @Valid @RequestBody ProductSaleSettingsRequest request) {
        ProductResponse response = backofficeProductService.updateSaleSettings(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/stock")
    public ResponseEntity<ApiResponse<ProductResponse>> adjustStock(
            @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                backofficeProductService.adjustStock(request)));
    }

}
