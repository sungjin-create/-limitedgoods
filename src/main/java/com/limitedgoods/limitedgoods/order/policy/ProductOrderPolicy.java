package com.limitedgoods.limitedgoods.order.policy;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.dto.request.OrderItemRequest;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductOrderPolicy {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public OrderProductValidationResult validate(List<OrderItemRequest> items) {
        Map<Long, Product> productMap = loadProductMap(items);

        LocalDateTime now = LocalDateTime.now();

        validateProductsExist(items, productMap);
        validatePurchasableProducts(items, productMap, now);
        validateLimitedProductSingleItemOrder(items, productMap);
        validateMaxPurchaseQuantity(items, productMap);

        Long limitedProductId = productMap.values()
                .stream()
                .filter(product -> product.getType() == ProductType.LIMITED)
                .map(Product::getId)
                .findFirst()
                .orElse(null);

        return new OrderProductValidationResult(
                limitedProductId
        );
    }

    private Map<Long, Product> loadProductMap(List<OrderItemRequest> items) {
        Set<Long> productIds = items.stream()
                .map(OrderItemRequest::productId)
                .collect(Collectors.toSet());

        return productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(
                        Product::getId,
                        Function.identity()
                ));
    }

    private void validateProductsExist(
            List<OrderItemRequest> items,
            Map<Long, Product> productMap
    ) {
        for (OrderItemRequest item : items) {
            if (!productMap.containsKey(item.productId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_PRODUCT_ID
                );
            }
        }
    }

    private void validatePurchasableProducts(
            List<OrderItemRequest> items,
            Map<Long, Product> productMap,
            LocalDateTime now
    ) {
        for (OrderItemRequest item : items) {
            Product product = productMap.get(item.productId());

            if (!product.isPurchasableAt(now)) {
                throw new BusinessException(
                        ErrorCode.INVALID_PRODUCT_SALE_STATUS
                );
            }
        }
    }

    private void validateLimitedProductSingleItemOrder(
            List<OrderItemRequest> items,
            Map<Long, Product> productMap
    ) {
        boolean containsLimited = false;

        for (OrderItemRequest item : items) {
            Product product = productMap.get(item.productId());

            if (product.getType() == ProductType.LIMITED) {
                containsLimited = true;
                break;
            }
        }

        if (containsLimited && items.size() != 1) {
            throw new BusinessException(
                    ErrorCode.LIMITED_PRODUCT_SINGLE_ORDER_ONLY);
        }
    }

    private void validateMaxPurchaseQuantity(
            List<OrderItemRequest> items,
            Map<Long, Product> productMap
    ) {
        for (OrderItemRequest item : items) {
            Product product = productMap.get(item.productId());

            Integer limit = product.getMaxPurchaseQuantity();

            if (limit != null && item.quantity() > limit) {
                throw new BusinessException(
                        ErrorCode.MAX_PURCHASE_QUANTITY_EXCEEDED
                );
            }
        }
    }
}
