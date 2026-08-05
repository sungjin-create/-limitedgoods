package com.limitedgoods.limitedgoods.user.dto.response;

public record RefreshResult(
        String accessToken,
        String refreshToken
) {
}