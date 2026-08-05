package com.limitedgoods.limitedgoods.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth.refresh-cookie")
public record RefreshCookieProperties(
        @NotBlank String name,
        boolean secure,
        @NotBlank String sameSite,
        @NotBlank String path,
        @NotNull Duration maxAge
) {
}