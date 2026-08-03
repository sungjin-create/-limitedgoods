package com.limitedgoods.limitedgoods.security.jwt;

import com.limitedgoods.limitedgoods.security.user.CustomUserDetails;
import com.limitedgoods.limitedgoods.user.entity.User;
import com.limitedgoods.limitedgoods.user.entity.UserStatus;
import com.limitedgoods.limitedgoods.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7);
            Claims claims = jwtUtil.parseClaims(token);

            Long userId = claims.get("userId", Long.class);
            String email = claims.getSubject();
            String claimedRole = claims.get("role", String.class);
            Object versionClaim = claims.get("ver");

            if (!(versionClaim instanceof Number version)) {
                throw new IllegalArgumentException("Missing token version");
            }

            User user = userRepository.findById(userId)
                    .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                    .filter(candidate -> candidate.getEmail().equals(email))
                    .filter(candidate -> candidate.getRole().name().equals(claimedRole))
                    .filter(candidate -> candidate.getTokenVersion() == version.longValue())
                    .orElseThrow(() -> new IllegalArgumentException("Invalidated user token"));

            CustomUserDetails userDetails = new CustomUserDetails(
                    user.getId(), user.getEmail(), user.getRole().name());

            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            authorities
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
