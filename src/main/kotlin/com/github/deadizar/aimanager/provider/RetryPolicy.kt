package com.github.deadizar.aimanager.provider

import kotlinx.coroutines.delay

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500L,
    val maxDelayMs: Long = 8_000L,
    val backoffMultiplier: Double = 2.0,
    val retryableStatuses: Set<Int> = setOf(429, 500, 502, 503, 504),
)

suspend fun <T> withRetry(policy: RetryPolicy, block: suspend () -> Result<T>): Result<T> {
    var delayMs = policy.initialDelayMs
    repeat(policy.maxAttempts - 1) { attempt ->
        val result = block()
        if (result.isSuccess) return result
        val ex = result.exceptionOrNull()
        if (ex is AiProviderError.AuthError || ex is AiProviderError.InvalidModelError) return result
        delay(delayMs.coerceAtMost(policy.maxDelayMs))
        delayMs = (delayMs * policy.backoffMultiplier).toLong()
    }
    return block()
}

