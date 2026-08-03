package com.limitedgoods.limitedgoods.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String password;
    private String name;
    @Enumerated(EnumType.STRING)
    private UserRole role;
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    public void changeRole(UserRole role) {
        this.role = role;
        invalidateTokens();
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
        invalidateTokens();
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
        invalidateTokens();
    }

    public void invalidateTokens() {
        this.tokenVersion++;
    }

}
