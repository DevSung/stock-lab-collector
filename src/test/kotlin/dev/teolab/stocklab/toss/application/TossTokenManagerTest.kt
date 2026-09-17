package dev.teolab.stocklab.toss.application

import dev.teolab.stocklab.toss.domain.AccessToken
import dev.teolab.stocklab.toss.domain.TokenIssuer
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TossTokenManagerTest {

    private val now = Instant.parse("2026-09-17T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val margin = Duration.ofMinutes(5)

    private fun countingIssuer(
        expiresIn: Duration = Duration.ofHours(1),
        beforeIssue: () -> Unit = {},
    ): Pair<TokenIssuer, AtomicInteger> {
        val counter = AtomicInteger()
        val issuer = TokenIssuer {
            beforeIssue()
            AccessToken.issuedAt("token-${counter.incrementAndGet()}", clock.instant(), expiresIn)
        }
        return issuer to counter
    }

    @Test
    fun `유효한 토큰이 캐시돼 있으면 재발급하지 않는다`() {
        val (issuer, counter) = countingIssuer()
        val manager = TossTokenManager(issuer, clock, margin)

        assertEquals("token-1", manager.accessToken())
        assertEquals("token-1", manager.accessToken())
        assertEquals(1, counter.get())
        assertEquals(1, manager.issueCount())
    }

    @Test
    fun `만료 마진에 들어온 토큰은 미리 갈아탄다`() {
        // 유효기간 4분 + 마진 5분 -> 발급 직후부터 사용 불가로 판정된다
        val (issuer, counter) = countingIssuer(expiresIn = Duration.ofMinutes(4))
        val manager = TossTokenManager(issuer, clock, margin)

        assertEquals("token-1", manager.accessToken())
        assertEquals("token-2", manager.accessToken())
        assertEquals(2, counter.get())
    }

    @Test
    fun `동시에 몰려도 발급은 정확히 한 번만 일어난다`() {
        val threadCount = 32
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        // 발급이 느릴수록 중복 발급이 잘 드러나므로 일부러 지연을 준다
        val (issuer, counter) = countingIssuer(beforeIssue = { Thread.sleep(50) })
        val manager = TossTokenManager(issuer, clock, margin)
        val pool = Executors.newFixedThreadPool(threadCount)

        try {
            val results = (1..threadCount).map {
                pool.submit<String> {
                    ready.countDown()
                    start.await()
                    manager.accessToken()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "스레드 준비 실패")
            start.countDown()

            val tokens = results.map { it.get(5, TimeUnit.SECONDS) }.toSet()
            assertEquals(setOf("token-1"), tokens, "모든 스레드가 같은 토큰을 받아야 한다")
            assertEquals(1, counter.get(), "토큰은 클라이언트당 1개뿐이라 중복 발급은 곧 무효화다")
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `refreshIfSame 은 실패에 쓰인 토큰이 아직 캐시에 있을 때만 재발급한다`() {
        val (issuer, counter) = countingIssuer()
        val manager = TossTokenManager(issuer, clock, margin)
        val stale = manager.accessToken()

        val refreshed = manager.refreshIfSame(stale)

        assertNotEquals(stale, refreshed)
        assertEquals(2, counter.get())
    }

    @Test
    fun `refreshIfSame 은 이미 갱신된 경우 재발급하지 않는다`() {
        val (issuer, counter) = countingIssuer()
        val manager = TossTokenManager(issuer, clock, margin)
        manager.accessToken()

        // 다른 스레드가 이미 갱신한 상황: 내가 쓴 토큰은 캐시에 없다
        val current = manager.refreshIfSame("이미-버려진-토큰")

        assertEquals("token-1", current)
        assertEquals(1, counter.get(), "동시 401 이 몰릴 때 서로의 토큰을 revoke 하면 안 된다")
    }

    @Test
    fun `currentToken 은 발급을 유발하지 않는다`() {
        val (issuer, counter) = countingIssuer()
        val manager = TossTokenManager(issuer, clock, margin)

        assertEquals(null, manager.currentToken())
        assertEquals(0, counter.get())
    }
}
