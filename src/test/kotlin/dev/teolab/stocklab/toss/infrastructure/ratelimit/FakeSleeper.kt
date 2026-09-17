package dev.teolab.stocklab.toss.infrastructure.ratelimit

import java.time.Duration
import java.time.Instant

/**
 * 실제로 자지 않고 대기 기록만 남기는 Sleeper. 시계도 함께 앞으로 감는다.
 * 이게 없으면 백오프 검증에 진짜로 수 초가 걸린다.
 *
 * 두 경로를 구분해서 기록한다.
 *   sleepUntil  rate limiter 의 페이싱
 *   sleep       재시도 백오프
 * 백오프로 시계가 크게 앞당겨지면 페이싱 대기는 0 이 되므로, 호출 횟수를 보려면 나눠 세야 한다.
 */
class FakeSleeper(private val clock: MutableClock) : Sleeper {
    val waits = mutableListOf<Duration>()

    /** rate limiter 가 페이싱을 위해 부른 기록. 대기 0 도 포함된다(= 퍼밋 획득 횟수). */
    val pacingWaits = mutableListOf<Duration>()

    /** 재시도 백오프로 잔 기록. */
    val backoffWaits = mutableListOf<Duration>()

    override fun sleepUntil(target: Instant) {
        val wait = Duration.between(clock.instant(), target)
        val effective = if (wait.isNegative || wait.isZero) Duration.ZERO else wait
        waits += effective
        pacingWaits += effective
        if (!effective.isZero) clock.advance(effective)
    }

    override fun sleep(duration: Duration) {
        waits += duration
        backoffWaits += duration
        if (!duration.isNegative && !duration.isZero) clock.advance(duration)
    }

    /** 실제로 기다린 시간만(0 대기는 빼고). */
    fun actualWaits(): List<Duration> = waits.filter { !it.isZero }
}
