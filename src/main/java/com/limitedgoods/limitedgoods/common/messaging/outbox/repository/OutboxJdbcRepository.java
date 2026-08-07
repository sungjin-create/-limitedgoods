package com.limitedgoods.limitedgoods.common.messaging.outbox.repository;

import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<OutboxMessage> lockPublishable(int batchSize) {
        return jdbcTemplate.query("""
            SELECT id, topic, event_key, payload::text, attempts
            FROM outbox_event
            WHERE status IN ('PENDING', 'FAILED')
              AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """,
                (rs, rowNum) -> new OutboxMessage(
                        rs.getObject("id", UUID.class),
                        rs.getString("topic"),
                        rs.getString("event_key"),
                        rs.getString("payload"),
                        rs.getInt("attempts")
                ),
                batchSize
        );
    }

    public void markPublished(UUID id) {
        jdbcTemplate.update("""
            UPDATE outbox_event
            SET status = 'PUBLISHED',
                published_at = CURRENT_TIMESTAMP,
                last_error = NULL
            WHERE id = ?
            """, id);
    }

    public void markFailed(UUID id, String error) {
        jdbcTemplate.update("""
            UPDATE outbox_event
            SET status = 'FAILED',
                attempts = attempts + 1,
                next_attempt_at =
                    CURRENT_TIMESTAMP
                    + make_interval(secs => LEAST(300, POWER(2, attempts)::int)),
                last_error = ?
            WHERE id = ?
            """, abbreviate(error), id);
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), 1000));
    }
}