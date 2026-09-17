package dev.teolab.stocklab.toss.infrastructure.ratelimit

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 테스트에서 손으로 감을 수 있는 시계. */
class MutableClock(
    private var now: Instant,
    private val zone: ZoneId = ZoneId.of("UTC"),
) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    fun advance(duration: Duration) { now = now.plus(duration) }
}
