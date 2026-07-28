package com.limitedgoods.limitedgoods.product.dto;

import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private int price;
    private int initialStock;
    private int stock;
    private int soldCount;
    private Integer maxPurchaseQuantity;
    private ProductType type;
    private ProductStatus status;
    private LocalDateTime saleStartAt;
    private LocalDateTime saleEndAt;
}
