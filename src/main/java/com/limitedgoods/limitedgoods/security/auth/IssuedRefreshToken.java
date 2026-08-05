package com.limitedgoods.limitedgoods.security.auth;

import com.limitedgoods.limitedgoods.user.entity.User;

public record IssuedRefreshToken(
        User user,
        String token
) {
}
