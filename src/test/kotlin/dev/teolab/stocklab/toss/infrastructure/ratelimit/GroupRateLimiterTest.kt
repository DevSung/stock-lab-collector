package dev.teolab.stocklab.toss.infrastructure.ratelimit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupRateLimiterTest {

    private val start = Instant.parse("2026-09-17T09:00:00Z")

    private fun fixture(safetyRatio: Double = 0.5): Triple<MutableClock, FakeSleeper, GroupRateLimiter> {
        val clock = MutableClock(start)
        val sleeper = FakeSleeper(clock)
        return Triple(clock, sleeper, GroupRateLimiter(clock, sleeper, safetyRatio))
    }

    @Test
    fun `문서 한도의 절반을 균등 간격으로 환산한다`() {
        val (_, _, limiter) = fixture()

        // 20/s 의 절반 = 10/s -> 100ms 간격
        assertEquals(Duration.ofMillis(100), limiter.intervalOf(ApiGroup.MARKET_DATA_CHART))
        // 10/s -> 5/s -> 200ms
        assertEquals(Duration.ofMillis(200), limiter.intervalOf(ApiGroup.STOCK_TRADING_TREND))
        // 5/s -> 2.5/s -> 400ms
        assertEquals(Duration.ofMillis(400), limiter.intervalOf(ApiGroup.STOCK))
        // 1/s -> 0.5/s -> 2초
        assertEquals(Duration.ofSeconds(2), limiter.intervalOf(ApiGroup.STOCK_ALL))
    }

    @Test
    fun `첫 호출은 기다리지 않는다`() {
        val (_, sleeper, limiter) = fixture()

        limiter.acquire(ApiGroup.STOCK_TRADING_TREND)

        assertTrue(sleeper.actualWaits().isEmpty())
    }

    @Test
    fun `연속 호출은 간격만큼 균등하게 벌어진다`() {
        val (_, sleeper, limiter) = fixture()

        repeat(4) { limiter.acquire(ApiGroup.STOCK_TRADING_TREND) }

        // 첫 호출은 즉시, 이후 3번은 200ms 씩
        assertEquals(listOf(200L, 200L, 200L), sleeper.actualWaits().map { it.toMillis() })
    }

    @Test
    fun `그룹이 다르면 서로 기다리지 않는다`() {
        val (_, sleeper, limiter) = fixture()

        limiter.acquire(ApiGroup.MARKET_DATA_CHART)
        limiter.acquire(ApiGroup.STOCK_TRADING_TREND)
        limiter.acquire(ApiGroup.STOCK)

        // 토스 한도가 그룹별 독립이므로 여기서도 독립이어야 한다
        assertTrue(sleeper.actualWaits().isEmpty())
    }

    @Test
    fun `호출 사이에 시간이 충분히 흘렀으면 기다리지 않는다`() {
        val (clock, sleeper, limiter) = fixture()

        limiter.acquire(ApiGroup.STOCK_TRADING_TREND)
        clock.advance(Duration.ofSeconds(5))
        limiter.acquire(ApiGroup.STOCK_TRADING_TREND)

        assertTrue(sleeper.actualWaits().isEmpty())
    }

    @Test
    fun `penalize 는 그룹 전체를 지정 시각까지 막는다`() {
        val (clock, sleeper, limiter) = fixture()

        limiter.penalize(ApiGroup.STOCK_TRADING_TREND, clock.instant().plusSeconds(7))
        limiter.acquire(ApiGroup.STOCK_TRADING_TREND)

        // 429 를 받은 쪽만 쉬면 다른 호출이 계속 429 를 유발한다. 그룹째로 멈춰야 한다.
        assertEquals(listOf(7_000L), sleeper.actualWaits().map { it.toMillis() })
    }

    @Test
    fun `penalize 는 다른 그룹에 영향을 주지 않는다`() {
        val (clock, sleeper, limiter) = fixture()

        limiter.penalize(ApiGroup.STOCK_TRADING_TREND, clock.instant().plusSeconds(7))
        limiter.acquire(ApiGroup.MARKET_DATA_CHART)

        assertTrue(sleeper.actualWaits().isEmpty())
    }

    @Test
    fun `safetyRatio 를 1 로 두면 문서 한도 그대로다`() {
        val (_, _, limiter) = fixture(safetyRatio = 1.0)

        assertEquals(Duration.ofMillis(50), limiter.intervalOf(ApiGroup.MARKET_DATA_CHART))
        assertEquals(Duration.ofSeconds(1), limiter.intervalOf(ApiGroup.STOCK_ALL))
    }

    @Test
    fun `safetyRatio 가 범위를 벗어나면 거부한다`() {
        val clock = MutableClock(start)
        assertThrows<IllegalArgumentException> { GroupRateLimiter(clock, FakeSleeper(clock), 0.0) }
        assertThrows<IllegalArgumentException> { GroupRateLimiter(clock, FakeSleeper(clock), 1.5) }
    }
}
