package com.limitedgoods.limitedgoods.backoffice.product.service;

import com.limitedgoods.limitedgoods.backoffice.product.dto.request.ProductRegisterRequest;
import com.limitedgoods.limitedgoods.backoffice.product.dto.request.ProductSaleSettingsRequest;
import com.limitedgoods.limitedgoods.backoffice.product.dto.request.StockAdjustmentRequest;
import com.limitedgoods.limitedgoods.backoffice.product.dto.response.ProductResponse;
import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import com.limitedgoods.limitedgoods.product.policy.ProductStatusPolicy;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.queue.service.QueueProductStateCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

import static com.limitedgoods.limitedgoods.product.entity.ProductStatus.*;

@Service
@RequiredArgsConstructor
public class BackofficeProductService {

    private final ProductRepository productRepository;
    private final ProductStatusPolicy productStatusPolicy;
    private final QueueProductStateCacheService queueProductStateCacheService;

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public ProductResponse registerProduct(ProductRegisterRequest productRegisterRequest) {
        String name = productRegisterRequest.getName();
        String description = productRegisterRequest.getDescription();
        int price = productRegisterRequest.getPrice();
        int initialStock = productRegisterRequest.getInitialStock();
        int soldCount = 0;
        Integer maxPurchaseQuantity = productRegisterRequest.getMaxPurchaseQuantity();
        ProductType type = productRegisterRequest.getType();
        ProductStatus status = productRegisterRequest.getStatus();
        LocalDateTime saleStartAt = productRegisterRequest.getSaleStartAt();
        LocalDateTime saleEndAt = productRegisterRequest.getSaleEndAt();

        //상품 등록시 가능한 상태검사
        productStatusPolicy.validateRegisterStatus(status);
        //판매 시작, 판매 끝 값 검사
        productStatusPolicy.validateSaleSchedule(status, saleStartAt, saleEndAt);

        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setInitialStock(initialStock);
        product.setStock(initialStock);
        product.setSoldCount(soldCount);
        product.setMaxPurchaseQuantity(maxPurchaseQuantity);
        product.setType(type);
        product.setStatus(status);
        product.setSaleStartAt(saleStartAt);
        product.setSaleEndAt(saleEndAt);
        product.setUpdatedAt(LocalDateTime.now());

        Product saveProduct = productRepository.save(product);

        queueProductStateCacheService.syncAfterCommit(saveProduct);

        return toResponse(saveProduct);
    }

    @Transactional
    public ProductResponse updateSaleSettings(ProductSaleSettingsRequest request) {
        Product product = productRepository.findByIdWithLock(request.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PRODUCT_ID));

        ProductStatus nextStatus = request.saleStartAt() != null
                && request.saleStartAt().isAfter(LocalDateTime.now())
                ? SCHEDULED
                : ACTIVE;

        productStatusPolicy.validateTransition(product.getStatus(), nextStatus);
        productStatusPolicy.validateSaleSchedule(nextStatus, request.saleStartAt(), product.getSaleEndAt());

        product.setType(request.type());
        product.setStatus(nextStatus);
        product.setSaleStartAt(request.saleStartAt());
        product.setUpdatedAt(LocalDateTime.now());

        queueProductStateCacheService.syncAfterCommit(product);

        return toResponse(product);
    }

    @Transactional
    public ProductResponse adjustStock(StockAdjustmentRequest request) {
        Product product = productRepository.findByIdWithLock(request.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PRODUCT_ID));

        int quantity = request.getQuantity();
        int currentStock = product.getStock();
        int adjustedStock;

        ProductStatus status = product.getStatus();

        if(status == ACTIVE || status == ARCHIVED) {
            throw new BusinessException(ErrorCode.STOCK_ADJUSTMENT_NOT_ALLOWED_STATUS, "현재 STATUS = " + status);
        }

        switch (request.getAdjustmentType()) {
            case INCREASE -> {
                if (quantity == 0) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
                long increasedStock = (long) currentStock + quantity;
                if (increasedStock > Integer.MAX_VALUE) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
                adjustedStock = (int) increasedStock;
            }
            case DECREASE -> {
                if (quantity == 0) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
                if (currentStock < quantity) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
                }
                adjustedStock = currentStock - quantity;
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        product.setStock(adjustedStock);
        product.setUpdatedAt(LocalDateTime.now());

        queueProductStateCacheService.syncAfterCommit(product);

        return toResponse(product);
    }


    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .initialStock(product.getInitialStock())
                .stock(product.getStock())
                .soldCount(product.getSoldCount())
                .maxPurchaseQuantity(product.getMaxPurchaseQuantity())
                .type(product.getType())
                .status(product.getStatus())
                .saleStartAt(product.getSaleStartAt())
                .saleEndAt(product.getSaleEndAt())
                .build();
    }

}
