package com.limitedgoods.limitedgoods.messaging.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limitedgoods.limitedgoods.messaging.contract.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(
            String topic,
            String eventKey,
            String aggregateType,
            String aggregateId,
            String eventType,
            int eventVersion,
            Object data
    ) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        EventEnvelope<Object> envelope = new EventEnvelope<>(
                eventId,
                eventType,
                eventVersion,
                occurredAt,
                aggregateType,
                aggregateId,
                data
        );

        try {
            String payload = objectMapper.writeValueAsString(envelope);

            jdbcTemplate.update("""
                INSERT INTO outbox_event (
                    id, aggregate_type, aggregate_id,
                    event_type, event_version,
                    topic, event_key, payload,
                    status, attempts, next_attempt_at, created_at
                )
                VALUES (
                    :id, :aggregateType, :aggregateId,
                    :eventType, :eventVersion,
                    :topic, :eventKey, CAST(:payload AS jsonb),
                    'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                    new MapSqlParameterSource()
                            .addValue("id", eventId)
                            .addValue("aggregateType", aggregateType)
                            .addValue("aggregateId", aggregateId)
                            .addValue("eventType", eventType)
                            .addValue("eventVersion", eventVersion)
                            .addValue("topic", topic)
                            .addValue("eventKey", eventKey)
                            .addValue("payload", payload)
            );

            return eventId;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("이벤트 직렬화 실패", exception);
        }
    }
}
