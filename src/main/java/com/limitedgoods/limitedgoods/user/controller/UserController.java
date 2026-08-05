package com.limitedgoods.limitedgoods.user.controller;

import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import com.limitedgoods.limitedgoods.security.auth.RefreshCookieFactory;
import com.limitedgoods.limitedgoods.user.dto.request.UserLoginRequest;
import com.limitedgoods.limitedgoods.user.dto.request.UserSignUpRequest;
import com.limitedgoods.limitedgoods.user.dto.response.LoginResult;
import com.limitedgoods.limitedgoods.user.dto.response.RefreshResult;
import com.limitedgoods.limitedgoods.user.dto.response.UserLoginResponse;
import com.limitedgoods.limitedgoods.user.dto.response.UserSignUpResponse;
import com.limitedgoods.limitedgoods.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RefreshCookieFactory refreshCookieFactory;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> userSignUp(
            @Valid @RequestBody UserSignUpRequest userSignUpRequest) {
        String userEmail = userService.signUpUsers(userSignUpRequest);
        return ResponseEntity.ok(ApiResponse.success(new UserSignUpResponse(userEmail)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> userLogin(
            @Valid @RequestBody UserLoginRequest userLoginRequest
    ) {

        LoginResult result = userService.loginUsers(userLoginRequest);

        ResponseCookie cookie = refreshCookieFactory.create(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(
                        new UserLoginResponse(result.accessToken())
                ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<UserLoginResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        RefreshResult result = userService.refreshAccessToken(refreshToken);

        ResponseCookie cookie = refreshCookieFactory.create(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(
                        new UserLoginResponse(result.accessToken())
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        userService.logout(refreshToken);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieFactory.expire().toString()
                )
                .body(ApiResponse.success());
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse> userInfo(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(userService.getUserInfo(email)));
    }

}
