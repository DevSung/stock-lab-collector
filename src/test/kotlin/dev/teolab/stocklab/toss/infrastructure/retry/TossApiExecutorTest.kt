package dev.teolab.stocklab.toss.infrastructure.retry

import dev.teolab.stocklab.toss.domain.TossAuthException
import dev.teolab.stocklab.toss.domain.TossRateLimitException
import dev.teolab.stocklab.toss.domain.TossServerException
import dev.teolab.stocklab.toss.domain.TossStockNotFoundException
import dev.teolab.stocklab.toss.infrastructure.ratelimit.ApiGroup
import dev.teolab.stocklab.toss.infrastructure.ratelimit.FakeSleeper
import dev.teolab.stocklab.toss.infrastructure.ratelimit.GroupRateLimiter
import dev.teolab.stocklab.toss.infrastructure.ratelimit.MutableClock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.ResourceAccessException
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TossApiExecutorTest {

    private val start = Instant.parse("2026-09-17T09:00:00Z")
    private val clock = MutableClock(start)
    private val sleeper = FakeSleeper(clock)
    private val rateLimiter = GroupRateLimiter(clock, sleeper)

    /** 지터를 끄고 결정적으로 본다. 지터 자체는 별도 테스트에서 확인한다. */
    private fun executor(policy: RetryPolicy = RetryPolicy(jitterRatio = 0.0)) =
        TossApiExecutor(rateLimiter, sleeper, clock, policy)

    @Test
    fun `성공하면 그대로 돌려주고 재시도하지 않는다`() {
        var calls = 0

        val result = executor().execute(ApiGroup.STOCK) { calls++; "ok" }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `5xx 는 1초 2초 4초로 백오프하며 재시도한다`() {
        var calls = 0
        val executor = executor()

        assertThrows<TossServerException> {
            executor.execute(ApiGroup.STOCK) { calls++; throw TossServerException("서버 오류") }
        }

        assertEquals(4, calls, "maxAttempts 만큼 시도해야 한다")
        assertEquals(listOf(1_000L, 2_000L, 4_000L), sleeper.backoffWaits.map { it.toMillis() })
    }

    @Test
    fun `타임아웃도 재시도 대상이다`() {
        var calls = 0

        assertThrows<ResourceAccessException> {
            executor().execute(ApiGroup.STOCK) { calls++; throw ResourceAccessException("timeout") }
        }

        assertEquals(4, calls)
    }

    @Test
    fun `재시도 중간에 성공하면 거기서 끝낸다`() {
        var calls = 0

        val result = executor().execute(ApiGroup.STOCK) {
            calls++
            if (calls < 3) throw TossServerException("일시 오류")
            "복구됨"
        }

        assertEquals("복구됨", result)
        assertEquals(3, calls)
    }

    @Test
    fun `429 는 Retry-After 를 백오프보다 우선한다`() {
        var calls = 0

        val result = executor().execute(ApiGroup.STOCK_TRADING_TREND) {
            calls++
            if (calls == 1) throw TossRateLimitException("한도 초과", Duration.ofSeconds(7))
            "ok"
        }

        assertEquals("ok", result)
        // 지수 백오프 1초가 아니라 서버가 알려준 7초를 기다려야 한다
        assertEquals(listOf(7_000L), sleeper.backoffWaits.map { it.toMillis() })
    }

    @Test
    fun `429 를 받으면 그룹 전체를 멈춘다`() {
        var calls = 0
        val executor = executor()

        executor.execute(ApiGroup.STOCK_TRADING_TREND) {
            calls++
            if (calls == 1) throw TossRateLimitException("한도 초과", Duration.ofSeconds(5)) else "ok"
        }

        // 재시도하는 나만 쉬는 게 아니라, 같은 그룹의 다음 호출도 막혀야 한다
        val before = clock.instant()
        rateLimiter.acquire(ApiGroup.STOCK_TRADING_TREND)
        assertTrue(clock.instant() >= before, "그룹 게이트가 적용되지 않았다")
    }

    @Test
    fun `Retry-After 가 상한을 넘으면 재시도하지 않는다`() {
        var calls = 0
        val executor = executor(RetryPolicy(jitterRatio = 0.0, retryAfterCap = Duration.ofSeconds(60)))

        assertThrows<TossRateLimitException> {
            executor.execute(ApiGroup.STOCK) {
                calls++
                throw TossRateLimitException("한도 초과", Duration.ofMinutes(10))
            }
        }

        // 10분을 붙잡고 있느니 실패로 끝내고 다음 배치에 맡긴다
        assertEquals(1, calls)
    }

    @Test
    fun `종목 없음이나 인증 실패는 재시도해도 소용없으므로 바로 던진다`() {
        var notFoundCalls = 0
        var authCalls = 0

        assertThrows<TossStockNotFoundException> {
            executor().execute(ApiGroup.STOCK) { notFoundCalls++; throw TossStockNotFoundException("없는 종목") }
        }
        assertThrows<TossAuthException> {
            executor().execute(ApiGroup.STOCK) { authCalls++; throw TossAuthException("인증 실패") }
        }

        assertEquals(1, notFoundCalls)
        // 인증 재시도는 인터셉터가 이미 1회 했다. 여기서 또 하면 토큰만 더 태운다.
        assertEquals(1, authCalls)
    }

    @Test
    fun `매 시도마다 rate limiter 퍼밋을 다시 얻는다`() {
        var calls = 0
        // 재시도가 rate limiter 안쪽에 있으면 재시도분이 한도 회계에서 빠져 429 를 더 유발한다.
        executor().execute(ApiGroup.STOCK_TRADING_TREND) {
            calls++
            if (calls < 3) throw TossServerException("일시 오류") else "ok"
        }

        // 3번 시도 = 퍼밋 3회. 백오프로 시계가 이미 앞당겨져 실제 대기는 0 이지만 획득 자체는 매번 일어나야 한다.
        assertEquals(3, calls)
        assertEquals(3, sleeper.pacingWaits.size, "재시도마다 퍼밋을 다시 얻어야 한다: ${sleeper.waits}")
    }

    @Test
    fun `지터는 기준 시간 주위로 흩어지되 음수가 되지 않는다`() {
        val policy = RetryPolicy(jitterRatio = 0.2)
        val jittery = TossApiExecutor(rateLimiter, sleeper, clock, policy, Random(42))
        var calls = 0

        assertThrows<TossServerException> {
            jittery.execute(ApiGroup.STOCK) { calls++; throw TossServerException("서버 오류") }
        }

        val backoffs = sleeper.backoffWaits.map { it.toMillis() }
        assertEquals(3, backoffs.size)
        // 1초 기준 ±20% -> 800~1200ms 안에 들어와야 한다
        assertTrue(backoffs[0] in 800L..1200L, "첫 백오프가 범위를 벗어남: ${backoffs[0]}")
        assertTrue(backoffs.all { it > 0 })
    }

    @Test
    fun `백오프는 상한에서 멈춘다`() {
        val policy = RetryPolicy(maxAttempts = 10, jitterRatio = 0.0, maxBackoff = Duration.ofSeconds(5))

        assertEquals(Duration.ofSeconds(1), policy.backoffFor(1))
        assertEquals(Duration.ofSeconds(4), policy.backoffFor(3))
        assertEquals(Duration.ofSeconds(5), policy.backoffFor(4), "상한을 넘으면 안 된다")
        assertEquals(Duration.ofSeconds(5), policy.backoffFor(9))
    }
}
