package com.limitedgoods.limitedgoods.common.messaging.inbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean tryInsert(String consumerName, UUID eventId) {
        int inserted = jdbcTemplate.update("""
            INSERT INTO processed_event (
                consumer_name, event_id, processed_at
            )
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """, consumerName, eventId);

        return inserted == 1;
    }
}