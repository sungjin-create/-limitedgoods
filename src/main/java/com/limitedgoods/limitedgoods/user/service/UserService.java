package com.limitedgoods.limitedgoods.user.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.security.auth.IssuedRefreshToken;
import com.limitedgoods.limitedgoods.security.auth.RefreshTokenService;
import com.limitedgoods.limitedgoods.security.jwt.JwtUtil;
import com.limitedgoods.limitedgoods.user.dto.response.LoginResult;
import com.limitedgoods.limitedgoods.user.dto.response.RefreshResult;
import com.limitedgoods.limitedgoods.user.dto.response.UserInfoResponse;
import com.limitedgoods.limitedgoods.user.dto.request.UserLoginRequest;
import com.limitedgoods.limitedgoods.user.dto.response.UserLoginResponse;
import com.limitedgoods.limitedgoods.user.dto.request.UserSignUpRequest;
import com.limitedgoods.limitedgoods.user.entity.UserRole;
import com.limitedgoods.limitedgoods.user.entity.User;
import com.limitedgoods.limitedgoods.user.entity.UserStatus;
import com.limitedgoods.limitedgoods.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final RefreshTokenService refreshTokenService;

    public String signUpUsers(UserSignUpRequest userSignUpRequest) {
        String usersName = userSignUpRequest.name();
        String usersEmail = userSignUpRequest.email();
        String usersPassword = userSignUpRequest.password();
        String confirmPassword = userSignUpRequest.confirmPassword();

        checkSignUpUsers(usersName, usersEmail, usersPassword, confirmPassword);

        User signUpUser = new User();
        signUpUser.setName(usersName);
        signUpUser.setEmail(usersEmail);
        signUpUser.setPassword(passwordEncoder.encode(usersPassword));
        signUpUser.setCreatedAt(LocalDateTime.now());
        signUpUser.setRole(UserRole.USER);
        signUpUser.setStatus(UserStatus.ACTIVE);
        signUpUser.setTokenVersion(0);

        try {
            userRepository.saveAndFlush(signUpUser);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        return usersEmail;
    }

    private void checkSignUpUsers(
            String usersName,
            String usersEmail,
            String usersPassword,
            String confirmPassword
    ) {
        if (usersName == null || usersEmail == null || usersPassword == null || confirmPassword == null
                || usersName.isBlank() || usersEmail.isBlank()
                || usersPassword.isBlank() || confirmPassword.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (!usersPassword.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }

        if (userRepository.existsByEmail(usersEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    public LoginResult loginUsers(UserLoginRequest userLoginRequest) {
        String email = userLoginRequest.email();
        String password = userLoginRequest.password();

        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String accessToken = createAccessToken(user);

        IssuedRefreshToken refreshToken =
                refreshTokenService.issue(user);

        return new LoginResult(
                accessToken,
                refreshToken.token()
        );
    }

    public RefreshResult refreshAccessToken(String rawRefreshToken) {
        IssuedRefreshToken rotated =
                refreshTokenService.rotate(rawRefreshToken);

        String accessToken = createAccessToken(rotated.user());

        return new RefreshResult(
                accessToken,
                rotated.token()
        );
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    public UserInfoResponse getUserInfo(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return new UserInfoResponse(
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private String createAccessToken(User user) {
        return jwtUtil.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getTokenVersion()
        );
    }
}
