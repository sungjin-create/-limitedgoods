package com.limitedgoods.limitedgoods.security.jwt;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank
        String secretBase64,

        @NotNull
        Duration accessExpiration
) {

    @AssertTrue(message = "JWT secret은 Base64 디코딩 후 32바이트 이상이어야 합니다.")
    public boolean isSecretStrongEnough() {
        try {
            return Base64.getDecoder()
                    .decode(secretBase64)
                    .length >= 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @AssertTrue(message = "Access token 만료 시간은 30분 이하여야 합니다.")
    public boolean isAccessExpirationAllowed() {
        return accessExpiration != null
                && !accessExpiration.isNegative()
                && !accessExpiration.isZero()
                && accessExpiration.compareTo(
                Duration.ofMinutes(30)
        ) <= 0;
    }
}