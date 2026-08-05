package com.limitedgoods.limitedgoods.security.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth.refresh-token")
public record RefreshTokenProperties(
        @NotNull Duration expiration
) {

    @AssertTrue(message = "Refresh Token 만료 시간은 0보다 커야 합니다.")
    public boolean isExpirationValid() {
        return expiration != null
                && !expiration.isZero()
                && !expiration.isNegative();
    }
}