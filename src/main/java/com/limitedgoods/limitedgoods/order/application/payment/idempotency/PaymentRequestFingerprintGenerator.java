package com.limitedgoods.limitedgoods.order.application.payment.idempotency;

import com.limitedgoods.limitedgoods.payment.dto.PaymentRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class PaymentRequestFingerprintGenerator {
    public String generate(
            Long orderId,
            PaymentRequest request
    ) {
        String source = orderId + ":" + request.isForceFail();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(source.getBytes(StandardCharsets.UTF_8))
            );

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "결제 요청 fingerprint 생성 실패",
                    exception
            );
        }
    }
}
