package com.limitedgoods.limitedgoods.order.application.create;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.create.dto.OrderStockReservationResult;
import com.limitedgoods.limitedgoods.order.dto.request.OrderItemRequest;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderStockReservationService {

    private final ProductRepository productRepository;

    /**
     * 반드시 이미 시작된 주문 생성 트랜잭션 안에서 호출해야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderStockReservationResult reserve(
            List<OrderItemRequest> items
    ) {
        long totalPrice = 0L;

        List<OrderItem> orderItems = new ArrayList<>();
        Set<Long> productIds = new HashSet<>();

        List<OrderItemRequest> sortedItems = items.stream()
                .sorted(Comparator.comparing(OrderItemRequest::productId))
                .toList();

        for (OrderItemRequest item : sortedItems) {
            Product product = getProduct(item.productId());

            decreaseStock(product.getId(), item.quantity());

            long lineTotalPrice = calculateLineTotalPrice(product.getPrice(), item.quantity());

            totalPrice = Math.addExact(totalPrice, lineTotalPrice);

            productIds.add(product.getId());

            orderItems.add(
                    createOrderItem(
                            product,
                            item.quantity(),
                            lineTotalPrice
                    )
            );
        }

        return new OrderStockReservationResult(totalPrice, orderItems, productIds);
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PRODUCT_ID));
    }

    private void decreaseStock(Long productId, int quantity) {
        int updated =
                productRepository.decreaseStockIfPurchasable(
                        productId,
                        quantity,
                        ProductStatus.ACTIVE,
                        ProductStatus.SCHEDULED
                );

        if (updated == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private long calculateLineTotalPrice(int price, int quantity) {
        try {
            return Math.multiplyExact((long) price, quantity);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private OrderItem createOrderItem(Product product, int quantity, long lineTotalPrice) {
        return OrderItem.builder()
                .product(product)
                .quantity(quantity)
                .price(product.getPrice())
                .lineTotalPrice(lineTotalPrice)
                .purchaseLimitAtOrder(
                        product.getMaxPurchaseQuantity()
                )
                .build();
    }
}