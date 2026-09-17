package dev.teolab.stocklab.toss.infrastructure.retry

import java.time.Duration

/**
 * 재시도 정책.
 *
 * [retryAfterCap] 을 넘는 Retry-After 가 오면 재시도하지 않고 실패시킨다.
 * 배치가 몇 분씩 멈춰 있는 것보다 실패로 끝내고 나중에 다시 도는 편이 낫다.
 */
data class RetryPolicy(
    val maxAttempts: Int = 4,
    val baseBackoff: Duration = Duration.ofSeconds(1),
    val multiplier: Double = 2.0,
    val maxBackoff: Duration = Duration.ofSeconds(30),
    val jitterRatio: Double = 0.2,
    val retryAfterCap: Duration = Duration.ofSeconds(60),
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts 는 1 이상이어야 한다: $maxAttempts" }
        require(multiplier >= 1.0) { "multiplier 는 1 이상이어야 한다: $multiplier" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio 는 0..1 이어야 한다: $jitterRatio" }
    }

    /** 1s -> 2s -> 4s ... maxBackoff 에서 멈춘다. [attempt] 는 1부터. */
    fun backoffFor(attempt: Int): Duration {
        val scaled = baseBackoff.toMillis() * Math.pow(multiplier, (attempt - 1).toDouble())
        return minOf(Duration.ofMillis(scaled.toLong()), maxBackoff)
    }
}
