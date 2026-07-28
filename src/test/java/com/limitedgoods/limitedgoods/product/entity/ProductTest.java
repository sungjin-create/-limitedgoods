package com.limitedgoods.limitedgoods.product.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Test
    void activeProductWithinSaleWindowIsPurchasable() {
        Product product = product(ProductStatus.ACTIVE, now.minusHours(1), now.plusHours(1));

        assertThat(product.isPurchasableAt(now)).isTrue();
    }

    @Test
    void scheduledProductBecomesPurchasableAtStartEvenBeforeSchedulerChangesStatus() {
        Product product = product(ProductStatus.SCHEDULED, now, now.plusHours(1));

        assertThat(product.isPurchasableAt(now)).isTrue();
    }

    @Test
    void futureScheduledAndEndedProductsAreNotPurchasable() {
        Product future = product(ProductStatus.SCHEDULED, now.plusSeconds(1), now.plusHours(1));
        Product ended = product(ProductStatus.ACTIVE, now.minusHours(2), now);

        assertThat(future.isPurchasableAt(now)).isFalse();
        assertThat(ended.isPurchasableAt(now)).isFalse();
    }

    @Test
    void nonSaleStatusesAreNotPurchasable() {
        for (ProductStatus status : new ProductStatus[]{
                ProductStatus.DRAFT, ProductStatus.PREPARING,
                ProductStatus.PAUSED, ProductStatus.HIDDEN, ProductStatus.ARCHIVED
        }) {
            assertThat(product(status, null, null).isPurchasableAt(now)).isFalse();
        }
    }

    private Product product(
            ProductStatus status,
            LocalDateTime saleStartAt,
            LocalDateTime saleEndAt
    ) {
        Product product = new Product();
        product.setStatus(status);
        product.setSaleStartAt(saleStartAt);
        product.setSaleEndAt(saleEndAt);
        return product;
    }
}
