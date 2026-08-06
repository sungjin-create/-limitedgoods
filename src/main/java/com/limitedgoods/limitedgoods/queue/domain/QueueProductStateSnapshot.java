package com.limitedgoods.limitedgoods.queue.domain;

import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.entity.ProductType;

import java.time.LocalDateTime;

public record QueueProductStateSnapshot(
        ProductType type,
        ProductStatus status,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt
) {

    private static final String EMPTY_TIME = "-";

    public static QueueProductStateSnapshot from(Product product) {
        return new QueueProductStateSnapshot(
                product.getType(),
                product.getStatus(),
                product.getSaleStartAt(),
                product.getSaleEndAt()
        );
    }

    public String serialize() {
        return String.join(
                "|",
                type.name(),
                status.name(),
                format(saleStartAt),
                format(saleEndAt)
        );
    }

    public static QueueProductStateSnapshot deserialize(String value) {
        String[] fields = value.split("\\|", -1);
        if (fields.length != 4) {
            throw new IllegalArgumentException("Invalid queue product state snapshot");
        }

        return new QueueProductStateSnapshot(
                ProductType.valueOf(fields[0]),
                ProductStatus.valueOf(fields[1]),
                parse(fields[2]),
                parse(fields[3])
        );
    }

    public QueueProductState stateAt(LocalDateTime now) {
        if (type != ProductType.LIMITED) {
            return QueueProductState.UNSUPPORTED;
        }

        if (status != ProductStatus.ACTIVE && status != ProductStatus.SCHEDULED) {
            return QueueProductState.CLOSED;
        }

        if (status == ProductStatus.SCHEDULED && saleStartAt == null) {
            return QueueProductState.CLOSED;
        }

        if (saleStartAt != null && now.isBefore(saleStartAt)) {
            return QueueProductState.CLOSED;
        }

        if (saleEndAt != null && !now.isBefore(saleEndAt)) {
            return QueueProductState.CLOSED;
        }

        return QueueProductState.OPEN;
    }

    private static String format(LocalDateTime value) {
        return value == null ? EMPTY_TIME : value.toString();
    }

    private static LocalDateTime parse(String value) {
        return EMPTY_TIME.equals(value) ? null : LocalDateTime.parse(value);
    }
}
