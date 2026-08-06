package com.limitedgoods.limitedgoods.backoffice.product.dto.request;

import com.limitedgoods.limitedgoods.product.entity.ProductType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record ProductSaleSettingsRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        @Positive(message = "상품 ID는 1 이상이어야 합니다.")
        Long id,

        @NotNull(message = "상품 타입은 필수입니다.")
        ProductType type,

        LocalDateTime saleStartAt
) {
}
