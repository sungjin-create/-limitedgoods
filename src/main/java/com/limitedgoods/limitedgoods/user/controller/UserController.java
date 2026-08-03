package com.limitedgoods.limitedgoods.user.controller;

import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import com.limitedgoods.limitedgoods.user.dto.request.UserLoginRequest;
import com.limitedgoods.limitedgoods.user.dto.request.UserSignUpRequest;
import com.limitedgoods.limitedgoods.user.dto.response.UserSignUpResponse;
import com.limitedgoods.limitedgoods.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> userSignUp(
            @Valid @RequestBody UserSignUpRequest userSignUpRequest) {
        String userEmail = userService.signUpUsers(userSignUpRequest);
        return ResponseEntity.ok(ApiResponse.success(new UserSignUpResponse(userEmail)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> userLogin(
            @Valid @RequestBody UserLoginRequest userLoginRequest) {
        return ResponseEntity.ok(ApiResponse.success(userService.loginUsers(userLoginRequest)));
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse> userInfo(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(userService.getUserInfo(email)));
    }

}
