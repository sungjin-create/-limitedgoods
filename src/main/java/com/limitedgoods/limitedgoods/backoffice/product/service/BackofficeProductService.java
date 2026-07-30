package com.limitedgoods.limitedgoods.backoffice.product.service;

import com.limitedgoods.limitedgoods.backoffice.product.dto.request.ProductRegisterRequest;
import com.limitedgoods.limitedgoods.backoffice.product.dto.request.ProductUpdateRequest;
import com.limitedgoods.limitedgoods.backoffice.product.dto.request.StockAdjustmentRequest;
import com.limitedgoods.limitedgoods.backoffice.product.dto.response.ProductListResponse;
import com.limitedgoods.limitedgoods.backoffice.product.dto.response.ProductResponse;
import com.limitedgoods.limitedgoods.backoffice.product.dto.response.ProductStockOverViewResponse;
import com.limitedgoods.limitedgoods.backoffice.product.dto.response.ProductSummaryResponse;
import com.limitedgoods.limitedgoods.backoffice.product.query.BackofficeProductQueryRepository;
import com.limitedgoods.limitedgoods.backoffice.product.query.ProductOrderSummaryQueryResult;
import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.application.history.ProductHistoryService;
import com.limitedgoods.limitedgoods.product.application.history.ProductSnapshot;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import com.limitedgoods.limitedgoods.product.policy.ProductStatusPolicy;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.queue.service.QueueProductStateCacheService;
import com.limitedgoods.limitedgoods.user.application.support.UserAccessService;
import com.limitedgoods.limitedgoods.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.limitedgoods.limitedgoods.product.entity.ProductStatus.*;

@Service
@RequiredArgsConstructor
public class BackofficeProductService {

    private final ProductRepository productRepository;
    private final BackofficeProductQueryRepository backofficeProductQueryRepository;
    private final ProductStatusPolicy productStatusPolicy;
    private final UserAccessService userAccessService;
    private final ProductHistoryService productHistoryService;
    private final QueueProductStateCacheService queueProductStateCacheService;

    @Transactional
    public ProductListResponse findBackofficeProductList(ProductStatus status) {
        ProductSummaryResponse productSummary =
                backofficeProductQueryRepository.findProductSummary();

        List<ProductResponse> productList;
        if(status == null) {
            productList = backofficeProductQueryRepository.findAllProducts();
        } else {
            productList = backofficeProductQueryRepository.findAllProductsByStatus(status);
        }

        return ProductListResponse.builder()
                .summary(productSummary)
                .products(productList)
                .build();
    }

    @Transactional
    public ProductResponse registerProduct (Long userId, ProductRegisterRequest productRegisterRequest) {
        User changedByUser = userAccessService.getUser(userId);

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

        productHistoryService.recordInitial(saveProduct, changedByUser);

        return toResponse(saveProduct);
    }

    @Transactional
    public ProductResponse updateProduct (Long userId, ProductUpdateRequest productUpdateRequest) {
        User changedByUser = userAccessService.getUser(userId);

        Long id = productUpdateRequest.getId();
        String nextName = productUpdateRequest.getName();
        String nextDescription = productUpdateRequest.getDescription();
        int nextPrice = productUpdateRequest.getPrice();
        Integer nextMaxPurchaseQuantity = productUpdateRequest.getMaxPurchaseQuantity();
        ProductType nextType = productUpdateRequest.getType();
        ProductStatus nextStatus = productUpdateRequest.getStatus();
        LocalDateTime nextSaleStartAt = productUpdateRequest.getSaleStartAt();
        LocalDateTime nextSaleEndAt = productUpdateRequest.getSaleEndAt();

        Product updateProduct = productRepository.findByIdWithLock(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PRODUCT_ID));

        ProductSnapshot before = ProductSnapshot.from(updateProduct);
        
        //상태변경 검사
        productStatusPolicy.validateTransition(updateProduct.getStatus(), nextStatus);

        //상태에 따른 일정 변경 검사
        productStatusPolicy.validateSaleSchedule(nextStatus, nextSaleStartAt, nextSaleEndAt);

        updateProduct.setName(nextName);
        updateProduct.setDescription(nextDescription);
        updateProduct.setPrice(nextPrice);
        updateProduct.setMaxPurchaseQuantity(nextMaxPurchaseQuantity);
        updateProduct.setType(nextType);
        updateProduct.setStatus(nextStatus);
        updateProduct.setSaleStartAt(nextSaleStartAt);
        updateProduct.setSaleEndAt(nextSaleEndAt);
        updateProduct.setUpdatedAt(LocalDateTime.now());
        
        ProductSnapshot after = ProductSnapshot.from(updateProduct);

        queueProductStateCacheService.syncAfterCommit(updateProduct);

        productHistoryService.recordProductUpdate(
                updateProduct,
                changedByUser,
                before,
                after,
                productUpdateRequest.getReason().trim());

        return toResponse(updateProduct);
    }

    @Transactional
    public ProductResponse adjustStock(Long userId, StockAdjustmentRequest request) {
        User changedByUser = userAccessService.getUser(userId);

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

        productHistoryService.recordStock(
                product,
                changedByUser,
                currentStock,
                adjustedStock,
                request.getReason().trim());

        return toResponse(product);
    }

    @Transactional
    public ProductStockOverViewResponse findProductStockOverView(Long productId){
        ProductOrderSummaryQueryResult queryResult =
                backofficeProductQueryRepository.findProductOrdersSummary(productId)
                .orElseThrow(()->new BusinessException(ErrorCode.INVALID_PRODUCT_ID));
        return ProductStockOverViewResponse.from(queryResult);
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
