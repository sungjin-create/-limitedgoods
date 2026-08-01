package com.limitedgoods.limitedgoods.queue.infrastructure.redis;

import com.limitedgoods.limitedgoods.common.infrastructure.redis.RedisKeyNamespace;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueueRedisKeys {

    private static final String PREFIX = RedisKeyNamespace.ROOT + ":queue";

    private static final Pattern
            ADMISSION_TRACK_KEY_PATTERN =
            Pattern.compile(
                    "^" + Pattern.quote(PREFIX) + ":\\{(\\d+)\\}" + ":admission:track:" + "(\\d+)" + "$"
            );

    private QueueRedisKeys() {
    }

    public static String waiting(Long productId) {
        return PREFIX + ":{" + productId + "}:waiting";
    }

    public static String admissionTrack(Long productId, Long userId) {
        return PREFIX + ":{" + productId + "}" + ":admission:track:" + userId;
    }

    public static String admissionToken(Long productId, String token) {
        return PREFIX + ":{" + productId + "}" + ":admission:token:" + token;
    }

    public static Optional<AdmissionTrackKey> parseAdmissionTrackKey(String key) {

        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = ADMISSION_TRACK_KEY_PATTERN.matcher(key);

        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            Long productId = Long.parseLong(matcher.group(1));

            Long userId = Long.parseLong(matcher.group(2));

            return Optional.of(new AdmissionTrackKey(productId, userId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static String activity(Long productId) {
        return PREFIX + ":{" + productId + "}:activity";
    }

    public static String activeProducts() {
        return PREFIX + ":active-products";
    }

    public static String admissionTokenPrefix(Long productId) {
        return PREFIX + ":{" + productId + "}:admission:token:";
    }

    public static String admissionPattern(Long productId) {
        return PREFIX + ":{" + productId + "}:admission:*";
    }

    public static String generation(Long productId) {
        return PREFIX + ":{" + productId + "}:generation";
    }

}