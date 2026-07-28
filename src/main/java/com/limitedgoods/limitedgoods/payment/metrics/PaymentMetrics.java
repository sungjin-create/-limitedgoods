package com.limitedgoods.limitedgoods.payment.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;

    public void recordPayment(String result, String reason) {
        meterRegistry.counter(
                "limitedgoods.payment",
                Tags.of(
                        "result", result,
                        "reason", normalize(reason)
                )
        ).increment();
    }

    private String normalize(String reason) {
        return reason == null || reason.isBlank()
                ? "none"
                : reason;
    }

    public void recordRevenue(long amount) {
        if (amount <= 0) {
            return;
        }

        meterRegistry.counter("limitedgoods.payment.revenue").increment(amount);
    }

}