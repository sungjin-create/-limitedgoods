package com.limitedgoods.limitedgoods.user.dto.response;

public record LoginResult(
        String accessToken,
        String refreshToken
) {
}