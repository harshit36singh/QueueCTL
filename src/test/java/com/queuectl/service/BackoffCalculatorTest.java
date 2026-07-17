package com.queuectl.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffCalculatorTest {

    @Test
    void zeroAttemptsMeansNoDelay() {
        assertThat(BackoffCalculator.delaySeconds(2, 0)).isEqualTo(0);
    }

    @Test
    void delayGrowsExponentiallyWithAttempts() {
        assertThat(BackoffCalculator.delaySeconds(2, 1)).isEqualTo(2);
        assertThat(BackoffCalculator.delaySeconds(2, 2)).isEqualTo(4);
        assertThat(BackoffCalculator.delaySeconds(2, 3)).isEqualTo(8);
        assertThat(BackoffCalculator.delaySeconds(2, 4)).isEqualTo(16);
    }

    @Test
    void respectsConfigurableBase() {
        assertThat(BackoffCalculator.delaySeconds(3, 2)).isEqualTo(9);
        assertThat(BackoffCalculator.delaySeconds(5, 3)).isEqualTo(125);
    }
}
