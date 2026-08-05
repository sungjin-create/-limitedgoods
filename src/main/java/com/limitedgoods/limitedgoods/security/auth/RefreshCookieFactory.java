package com.limitedgoods.limitedgoods.security.auth;

import com.limitedgoods.limitedgoods.security.config.RefreshCookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

    private final RefreshCookieProperties properties;

    public ResponseCookie create(String refreshToken) {
        return ResponseCookie
                .from(properties.name(), refreshToken)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(properties.maxAge())
                .build();
    }

    public ResponseCookie expire() {
        return ResponseCookie
                .from(properties.name(), "")
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(Duration.ZERO)
                .build();
    }
}