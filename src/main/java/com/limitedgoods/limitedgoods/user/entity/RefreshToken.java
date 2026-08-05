package com.limitedgoods.limitedgoods.user.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(
            User user,
            String tokenHash,
            long tokenVersion,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.tokenVersion = tokenVersion;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static RefreshToken create(
            User user,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        return new RefreshToken(
                user,
                tokenHash,
                user.getTokenVersion(),
                expiresAt,
                createdAt
        );
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null
                && expiresAt.isAfter(now)
                && user.getStatus() == UserStatus.ACTIVE
                && user.getTokenVersion() == tokenVersion;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }
}