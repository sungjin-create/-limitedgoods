package com.limitedgoods.limitedgoods.common.messaging.outbox.repository;

import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxDeadEventResponse;
import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<OutboxEvent> lockPublishable(int batchSize) {
        return jdbcTemplate.query("""
            SELECT id, topic, event_key, payload::text, attempts
            FROM outbox_event
            WHERE status IN ('PENDING', 'FAILED')
              AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """,
                (rs, rowNum) -> new OutboxEvent(
                        rs.getObject("id", UUID.class),
                        rs.getString("topic"),
                        rs.getString("event_key"),
                        rs.getString("payload"),
                        rs.getInt("attempts")
                ),
                batchSize
        );
    }

    public List<OutboxDeadEventResponse> findOutboxDeadEvents(int page, int size){
        long offset = (long) page * size;

        return jdbcTemplate.query("""
                SELECT id, aggregate_type, aggregate_id,
                    event_type, event_version, attempts,
                    last_error, created_at, dead_at
                FROM outbox_event
                WHERE status = 'DEAD'
                ORDER BY dead_at DESC, created_at DESC
                LIMIT ?
                OFFSET ?;
            """,
            (rs, rowNum) -> new OutboxDeadEventResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("aggregate_type"),
                    rs.getString("aggregate_id"),
                    rs.getString("event_type"),
                    rs.getInt("event_version"),
                    rs.getInt("attempts"),
                    rs.getString("last_error"),
                    rs.getObject("created_at", LocalDateTime.class),
                    rs.getObject("dead_at", LocalDateTime.class)
            ), size, offset
        );
    }

    public void markPublished(UUID id) {
        jdbcTemplate.update("""
            UPDATE outbox_event
            SET status = 'PUBLISHED',
                published_at = CURRENT_TIMESTAMP,
                next_attempt_at = NULL,
                last_error = NULL
            WHERE id = ?
            """, id);
    }

    public void markFailed(UUID id, int failureCount, Instant nextAttemptAt, String error) {
        jdbcTemplate.update("""
            UPDATE outbox_event
            SET status = 'FAILED',
                attempts = ?,
                next_attempt_at = ?,
                last_error = ?,
                dead_at = NULL
            WHERE id = ?
            """,
            failureCount,
            Timestamp.from(nextAttemptAt),
            abbreviate(error),
            id
        );
    }

    public void markDead(UUID id, int failureCount, String error) {
        jdbcTemplate.update("""
            UPDATE outbox_event
            SET status = 'DEAD',
                attempts = ?,
                next_attempt_at = NULL,
                last_error = ?,
                dead_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            failureCount,
            abbreviate(error),
            id
        );
    }

    public boolean requeueDead(UUID eventId) {
        int updated = jdbcTemplate.update("""
        UPDATE outbox_event
        SET status = 'PENDING',
            attempts = 0,
            next_attempt_at = CURRENT_TIMESTAMP,
            dead_at = NULL
        WHERE id = ?
          AND status = 'DEAD'
        """, eventId);

        return updated == 1;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), 1000));
    }
}