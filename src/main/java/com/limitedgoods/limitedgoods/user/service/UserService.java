package com.limitedgoods.limitedgoods.user.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.security.jwt.JwtUtil;
import com.limitedgoods.limitedgoods.user.dto.response.UserInfoResponse;
import com.limitedgoods.limitedgoods.user.dto.request.UserLoginRequest;
import com.limitedgoods.limitedgoods.user.dto.response.UserLoginResponse;
import com.limitedgoods.limitedgoods.user.dto.request.UserSignUpRequest;
import com.limitedgoods.limitedgoods.user.entity.UserRole;
import com.limitedgoods.limitedgoods.user.entity.User;
import com.limitedgoods.limitedgoods.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public String signUpUsers(UserSignUpRequest userSignUpRequest) {
        String usersName = userSignUpRequest.name();
        String usersEmail = userSignUpRequest.email();
        String usersPassword = userSignUpRequest.password();

        checkSignUpUsers(usersName, usersEmail, usersPassword);

        User signUpUser = new User();
        signUpUser.setName(usersName);
        signUpUser.setEmail(usersEmail);
        signUpUser.setPassword(passwordEncoder.encode(usersPassword));
        signUpUser.setCreatedAt(LocalDateTime.now());
        signUpUser.setRole(UserRole.USER);

        userRepository.save(signUpUser);
        return usersEmail;
    }

    private void checkSignUpUsers(String usersName, String usersEmail, String usersPassword) {
        if(usersName == null || usersEmail == null || usersPassword == null ||
                usersName.isEmpty() || usersEmail.isEmpty() || usersPassword.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (userRepository.existsByEmail(usersEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Transactional(readOnly = true)
    public UserLoginResponse loginUsers(UserLoginRequest userLoginRequest) {
        String email = userLoginRequest.email();
        String password = userLoginRequest.password();

        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new UserLoginResponse(accessToken);
    }

    public UserInfoResponse getUserInfo(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return UserInfoResponse.builder().name(user.getName()).email(user.getEmail()).createdAt(user.getCreatedAt()).build();
    }
}
