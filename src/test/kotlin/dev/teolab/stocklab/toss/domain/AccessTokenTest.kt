package dev.teolab.stocklab.toss.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessTokenTest {

    private val issuedAt = Instant.parse("2026-09-17T09:00:00Z")

    @Test
    fun `만료 마진 안쪽이면 아직 사용 가능하다`() {
        val token = AccessToken.issuedAt("abcdef123456", issuedAt, Duration.ofHours(1))

        assertTrue(token.isUsableAt(issuedAt, Duration.ofMinutes(5)))
        assertTrue(token.isUsableAt(issuedAt.plus(Duration.ofMinutes(54)), Duration.ofMinutes(5)))
    }

    @Test
    fun `만료 마진에 들어오면 더 이상 쓰지 않는다`() {
        val token = AccessToken.issuedAt("abcdef123456", issuedAt, Duration.ofHours(1))

        // 만료 4분 전 = 마진 5분 안쪽
        assertFalse(token.isUsableAt(issuedAt.plus(Duration.ofMinutes(56)), Duration.ofMinutes(5)))
        assertFalse(token.isUsableAt(issuedAt.plus(Duration.ofHours(2)), Duration.ofMinutes(5)))
    }

    @Test
    fun `마스킹은 앞 6자리만 남긴다`() {
        assertEquals("abcdef****", AccessToken.issuedAt("abcdef123456", issuedAt, Duration.ofHours(1)).masked())
        assertEquals("****", AccessToken.issuedAt("abc", issuedAt, Duration.ofHours(1)).masked())
    }

    @Test
    fun `toString 에 토큰 원문이 절대 들어가지 않는다`() {
        val secret = "super-secret-token-value"
        val token = AccessToken.issuedAt(secret, issuedAt, Duration.ofHours(1))

        assertFalse(token.toString().contains(secret))
    }

    @Test
    fun `빈 토큰이나 뒤집힌 유효기간은 만들 수 없다`() {
        assertThrows<IllegalArgumentException> { AccessToken("  ", issuedAt, issuedAt.plusSeconds(60)) }
        assertThrows<IllegalArgumentException> { AccessToken("abc", issuedAt, issuedAt.minusSeconds(1)) }
    }
}
