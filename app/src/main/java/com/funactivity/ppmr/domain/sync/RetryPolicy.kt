package com.funactivity.ppmr.domain.sync

/** Exponential backoff for failed uploads: 30s, 1m, 2m, 4m, then give up on attempt 5. */
object RetryPolicy {
    const val MAX_ATTEMPTS = 5
    private const val BASE_DELAY_MS = 30_000L

    fun shouldGiveUp(attemptsMade: Int): Boolean = attemptsMade >= MAX_ATTEMPTS

    /** Delay before the next try, after [attemptsMade] failed attempts (1 or more). */
    fun delayAfter(attemptsMade: Int): Long =
        BASE_DELAY_MS shl (attemptsMade.coerceIn(1, MAX_ATTEMPTS) - 1)
}
