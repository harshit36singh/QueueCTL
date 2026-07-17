package com.queuectl.service;

/**
 * delay = base ^ attempts seconds, per the retry/backoff spec.
 */
public final class BackoffCalculator {

    private BackoffCalculator() {
    }

    public static long delaySeconds(int backoffBase, int attempts) {
        if (attempts <= 0) {
            return 0;
        }
        return (long) Math.pow(backoffBase, attempts);
    }
}
