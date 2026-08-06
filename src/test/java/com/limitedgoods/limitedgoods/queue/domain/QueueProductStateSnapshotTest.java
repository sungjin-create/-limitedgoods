package com.limitedgoods.limitedgoods.queue.domain;

import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class QueueProductStateSnapshotTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

    @Test
    void scheduledProductOpensWhenStartTimePassesWithoutCacheRewrite() {
        QueueProductStateSnapshot snapshot = new QueueProductStateSnapshot(
                ProductType.LIMITED,
                ProductStatus.SCHEDULED,
                NOW.plusMinutes(1),
                NOW.plusHours(1)
        );
        QueueProductStateSnapshot restored = QueueProductStateSnapshot.deserialize(snapshot.serialize());

        assertThat(restored.stateAt(NOW)).isEqualTo(QueueProductState.CLOSED);
        assertThat(restored.stateAt(NOW.plusMinutes(1))).isEqualTo(QueueProductState.OPEN);
    }

    @Test
    void activeProductClosesAtSaleEndWithoutCacheRewrite() {
        QueueProductStateSnapshot snapshot = new QueueProductStateSnapshot(
                ProductType.LIMITED,
                ProductStatus.ACTIVE,
                null,
                NOW.plusMinutes(1)
        );

        assertThat(snapshot.stateAt(NOW)).isEqualTo(QueueProductState.OPEN);
        assertThat(snapshot.stateAt(NOW.plusMinutes(1))).isEqualTo(QueueProductState.CLOSED);
    }

    @Test
    void normalProductNeverUsesTheLimitedQueue() {
        QueueProductStateSnapshot snapshot = new QueueProductStateSnapshot(
                ProductType.NORMAL,
                ProductStatus.ACTIVE,
                null,
                null
        );

        assertThat(snapshot.stateAt(NOW)).isEqualTo(QueueProductState.UNSUPPORTED);
    }
}
