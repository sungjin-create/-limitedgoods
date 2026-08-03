package com.limitedgoods.limitedgoods.product.service;

import com.limitedgoods.limitedgoods.product.dto.ProductResponse;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

import static com.limitedgoods.limitedgoods.product.entity.ProductStatus.*;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final EnumSet<com.limitedgoods.limitedgoods.product.entity.ProductStatus> PUBLIC_STATUSES =
            EnumSet.of(PREPARING, SCHEDULED, ACTIVE, PAUSED);

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findProductByStatusIn(PREPARING, SCHEDULED, ACTIVE, PAUSED, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProduct(Pageable pageable, String keyword) {
        return productRepository.searchByKeywordAndStatusIn(pageable, keyword, PUBLIC_STATUSES)
                .map(this::toResponse);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .type(product.getType())
                .status(product.getStatus())
                .saleStartAt(product.getSaleStartAt())
                .saleEndAt(product.getSaleEndAt())
                .build();
    }

}
