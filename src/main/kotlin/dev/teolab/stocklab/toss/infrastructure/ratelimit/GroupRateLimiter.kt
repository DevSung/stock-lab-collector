package dev.teolab.stocklab.toss.infrastructure.ratelimit

import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 그룹별로 호출 간격을 **균등하게 벌리는** 페이싱 방식 rate limiter.
 *
 * 흔한 고정 윈도우(1초에 N개) 방식은 윈도우 경계에서 순간적으로 2N 개가 나간다.
 * 윈도우 끝에 N개, 다음 윈도우 시작에 N개가 몰리면 임의의 1초 구간 기준으로는 한도 초과다.
 * 여기서는 "다음 호출 가능 시각"만 들고 간격을 강제해서 그 버스트 자체를 없앤다.
 *
 * 실제 사용 한도는 문서상 한도의 [safetyRatio] 배다. 다른 곳에서 이미 호출을 썼을 수도 있고,
 * 서버의 집계 기준(슬라이딩 윈도우일 가능성)이 우리와 다를 수 있어 보수적으로 잡는다.
 */
class GroupRateLimiter(
    private val clock: Clock,
    private val sleeper: Sleeper,
    private val safetyRatio: Double = DEFAULT_SAFETY_RATIO,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val gates = ConcurrentHashMap<ApiGroup, Gate>()

    init {
        require(safetyRatio > 0 && safetyRatio <= 1.0) { "safetyRatio 는 0 초과 1 이하여야 한다: $safetyRatio" }
    }

    /** 차례가 될 때까지 기다린다. 슬롯 예약은 잠금 안에서, 대기는 밖에서 한다. */
    fun acquire(group: ApiGroup) {
        val gate = gates.computeIfAbsent(group) { Gate(intervalOf(it)) }
        val startAt = gate.reserve(clock.instant())
        sleeper.sleepUntil(startAt)
    }

    /**
     * 429 를 받았을 때 그룹 전체를 [until] 까지 막는다.
     *
     * 한 요청이 429 를 받았는데 다른 호출이 그대로 진행하면 계속 429 가 난다.
     * 재시도하는 쪽만 기다려선 부족하고 그룹 전체가 멈춰야 한다.
     */
    fun penalize(group: ApiGroup, until: Instant) {
        val gate = gates.computeIfAbsent(group) { Gate(intervalOf(it)) }
        gate.blockUntil(until)
        log.warn("{} 그룹을 {} 까지 정지시킨다 (429 대응)", group, until)
    }

    fun intervalOf(group: ApiGroup): Duration {
        val effectivePerSecond = group.documentedLimitPerSecond * safetyRatio
        return Duration.ofNanos((1_000_000_000L / effectivePerSecond).toLong())
    }

    private class Gate(private val interval: Duration) {
        private var nextAllowedAt: Instant = Instant.EPOCH

        @Synchronized
        fun reserve(now: Instant): Instant {
            val startAt = maxOf(now, nextAllowedAt)
            nextAllowedAt = startAt.plus(interval)
            return startAt
        }

        @Synchronized
        fun blockUntil(until: Instant) {
            if (until.isAfter(nextAllowedAt)) nextAllowedAt = until
        }
    }

    companion object {
        /** 문서상 한도의 절반만 쓴다. */
        const val DEFAULT_SAFETY_RATIO = 0.5
    }
}
