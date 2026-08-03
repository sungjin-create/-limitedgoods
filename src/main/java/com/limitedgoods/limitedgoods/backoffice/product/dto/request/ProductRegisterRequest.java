package com.limitedgoods.limitedgoods.backoffice.product.dto.request;

import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductRegisterRequest {

    @NotBlank(message = "상품명은 필수입니다.")
    private String name;

    @Size(max = 1000, message = "상품 설명은 1,000자 이하여야 합니다.")
    private String description;

    @NotNull(message = "가격은 필수입니다.")
    @PositiveOrZero(message = "양수형태만 가능합니다.")
    private Integer price;

    @NotNull(message = "초도 재고는 필수입니다.")
    @Positive(message = "초도 재고는 1개 이상이어야 합니다.")
    private Integer initialStock;

    @Positive(message = "구매제한은 없거나, 1개 이상이어야 합니다.")
    private Integer maxPurchaseQuantity;

    @NotNull(message = "type은 필수입니다.")
    private ProductType type;

    @NotNull(message = "status는 필수입니다.")
    private ProductStatus status;

    private LocalDateTime saleStartAt;
    private LocalDateTime saleEndAt;

}
