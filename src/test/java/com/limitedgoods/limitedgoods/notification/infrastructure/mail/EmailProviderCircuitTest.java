package com.limitedgoods.limitedgoods.notification.infrastructure.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailProviderCircuitTest {

    private final EmailProviderCircuit circuit = new EmailProviderCircuit();

    @Test
    @DisplayName("설정된 차단 시간 동안 Circuit이 열린다")
    void openCircuit() {
        Instant now = Instant.parse("2026-07-24T01:00:00Z");

        circuit.open(now, Duration.ofMinutes(5));

        assertThat(circuit.isBlocked(now)).isTrue();
        assertThat(circuit.isBlocked(now.plusSeconds(299))).isTrue();
        assertThat(circuit.isBlocked(now.plusSeconds(300))).isFalse();
    }

    @Test
    @DisplayName("짧은 차단 요청은 기존 차단 시간을 단축하지 않는다")
    void doNotShortenBlockedUntil() {
        Instant now = Instant.parse("2026-07-24T01:00:00Z");

        circuit.open(now, Duration.ofMinutes(10));
        Instant originalBlockedUntil = circuit.getBlockedUntil();
        circuit.open(now.plusSeconds(60), Duration.ofMinutes(2));

        assertThat(circuit.getBlockedUntil()).isEqualTo(originalBlockedUntil);
    }

    @Test
    @DisplayName("긴 차단 요청은 차단 시간을 연장한다")
    void extendBlockedUntil() {
        Instant now = Instant.parse("2026-07-24T01:00:00Z");

        circuit.open(now, Duration.ofMinutes(5));
        circuit.open(now, Duration.ofMinutes(10));

        assertThat(circuit.getBlockedUntil()).isEqualTo(now.plusSeconds(600));
    }

    @Test
    @DisplayName("Circuit을 닫으면 즉시 차단이 해제된다")
    void closeCircuit() {
        Instant now = Instant.parse("2026-07-24T01:00:00Z");

        circuit.open(now, Duration.ofMinutes(5));
        circuit.close();

        assertThat(circuit.isBlocked(now)).isFalse();
        assertThat(circuit.getBlockedUntil()).isEqualTo(Instant.EPOCH);
    }
}
