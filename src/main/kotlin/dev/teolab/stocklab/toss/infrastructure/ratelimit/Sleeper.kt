package dev.teolab.stocklab.toss.infrastructure.ratelimit

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 대기 추상화.
 *
 * 테스트에서 진짜로 기다리지 않게 하려고 인터페이스로 뺐다.
 * 이게 없으면 백오프 시퀀스를 검증하는 데 실제로 몇 초씩 걸린다.
 */
interface Sleeper {
    fun sleepUntil(target: Instant)

    fun sleep(duration: Duration)
}

class ThreadSleeper(private val clock: Clock) : Sleeper {

    override fun sleepUntil(target: Instant) {
        val wait = Duration.between(clock.instant(), target)
        if (wait.isPositive) sleep(wait)
    }

    override fun sleep(duration: Duration) {
        if (!duration.isPositive) return
        try {
            Thread.sleep(duration.toMillis(), (duration.toNanosPart() % 1_000_000))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("대기 중 인터럽트됐다", e)
        }
    }
}

private val Duration.isPositive: Boolean get() = !isZero && !isNegative
