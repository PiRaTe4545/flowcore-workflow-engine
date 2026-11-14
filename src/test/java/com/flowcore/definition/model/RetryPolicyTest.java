
package com.flowcore.definition.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void shouldCreateValidRetryPolicy() {
        RetryPolicy policy = new RetryPolicy(
                5,
                Duration.ofSeconds(2),
                2.0,
                Duration.ofSeconds(30)
        );

        assertEquals(5, policy.maxAttempts());
        assertEquals(Duration.ofSeconds(2), policy.initialInterval());
        assertEquals(2.0, policy.backoffMultiplier());
        assertEquals(Duration.ofSeconds(30), policy.maxInterval());
    }

    @Test
    void shouldRejectZeroMaxAttempts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        0,
                        Duration.ofSeconds(2),
                        2.0,
                        Duration.ofSeconds(30)
                )
        );
    }

    @Test
    void shouldRejectNegativeMaxAttempts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        -1,
                        Duration.ofSeconds(2),
                        2.0,
                        Duration.ofSeconds(30)
                )
        );
    }

    @Test
    void shouldRejectNullInitialInterval() {
        assertThrows(
                NullPointerException.class,
                () -> new RetryPolicy(
                        3,
                        null,
                        2.0,
                        Duration.ofSeconds(30)
                )
        );
    }

    @Test
    void shouldRejectNonPositiveInitialInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        3,
                        Duration.ZERO,
                        2.0,
                        Duration.ofSeconds(30)
                )
        );
    }

    @Test
    void shouldRejectInvalidBackoffMultiplier() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        3,
                        Duration.ofSeconds(2),
                        0.5,
                        Duration.ofSeconds(30)
                )
        );
    }

    @Test
    void shouldRejectNaNBackoffMultiplier() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        3,
                        Duration.ofSeconds(2),
                        Double.NaN,
                        Duration.ofSeconds(30)
                )
        );
    }

    @Test
    void shouldRejectInfiniteBackoffMultiplier() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        3,
                        Duration.ofSeconds(2),
                        Double.POSITIVE_INFINITY,
                        Duration.ofSeconds(30)
                )
        );
    }

    @Test
    void shouldRejectNullMaxInterval() {
        assertThrows(
                NullPointerException.class,
                () -> new RetryPolicy(
                        3,
                        Duration.ofSeconds(2),
                        2.0,
                        null
                )
        );
    }

    @Test
    void shouldRejectMaxIntervalLessThanInitialInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(
                        3,
                        Duration.ofSeconds(10),
                        2.0,
                        Duration.ofSeconds(5)
                )
        );
    }

    @Test
    void shouldCalculateExponentialRetryDelay() {
        RetryPolicy policy = new RetryPolicy(
                5,
                Duration.ofSeconds(2),
                2.0,
                Duration.ofSeconds(30)
        );

        assertEquals(Duration.ofSeconds(2), policy.calculateDelay(1));
        assertEquals(Duration.ofSeconds(4), policy.calculateDelay(2));
        assertEquals(Duration.ofSeconds(8), policy.calculateDelay(3));
        assertEquals(Duration.ofSeconds(16), policy.calculateDelay(4));
        assertEquals(Duration.ofSeconds(30), policy.calculateDelay(5));
    }

    @Test
    void shouldRejectInvalidFailedAttempt() {
        RetryPolicy policy = new RetryPolicy(
                5,
                Duration.ofSeconds(2),
                2.0,
                Duration.ofSeconds(30)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.calculateDelay(0)
        );
    }

    @Test
    void shouldCapDelayForVeryLargeFailedAttempt() {
        RetryPolicy policy = new RetryPolicy(
                100,
                Duration.ofSeconds(2),
                2.0,
                Duration.ofSeconds(30)
        );

        assertEquals(
                Duration.ofSeconds(30),
                policy.calculateDelay(Integer.MAX_VALUE)
        );
    }
}
