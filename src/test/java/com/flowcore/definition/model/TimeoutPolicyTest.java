package com.flowcore.definition.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutPolicyTest {

    @Test
    void shouldCreateTimeoutPolicy() {
        TimeoutPolicy policy = new TimeoutPolicy(Duration.ofSeconds(30));

        assertEquals(Duration.ofSeconds(30), policy.timeout());
    }

    @Test
    void shouldRejectZeroTimeout() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimeoutPolicy(Duration.ZERO)
        );
    }

    @Test
    void shouldRejectNegativeTimeout() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimeoutPolicy(Duration.ofSeconds(-5))
        );
    }

    @Test
    void shouldRejectNullTimeout() {
        assertThrows(
                NullPointerException.class,
                () -> new TimeoutPolicy(null)
        );
    }
}