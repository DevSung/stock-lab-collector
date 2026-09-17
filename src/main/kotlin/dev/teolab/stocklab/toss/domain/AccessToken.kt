package dev.teolab.stocklab.toss.domain

import java.time.Duration
import java.time.Instant

/**
 * 토스증권 액세스 토큰.
 *
 * 클라이언트당 단 하나만 유효하다. 새로 발급하는 순간 이전 토큰은 즉시 무효화(token-revoked)되므로
 * 이 값은 애플리케이션 전체에서 [dev.teolab.stocklab.toss.application.TossTokenManager] 한 곳만 만들어야 한다.
 */
data class AccessToken(
    val value: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
) {
    init {
        require(value.isNotBlank()) { "토큰 값이 비어 있다" }
        require(expiresAt.isAfter(issuedAt)) { "만료 시각($expiresAt)이 발급 시각($issuedAt)보다 빠르다" }
    }

    /** 만료 [margin] 이전까지만 사용 가능한 것으로 본다. 호출 도중 만료되는 상황을 피하기 위한 여유분이다. */
    fun isUsableAt(now: Instant, margin: Duration): Boolean = now.plus(margin).isBefore(expiresAt)

    /** 로그에 남겨도 되는 형태. 토큰 원문은 어떤 경우에도 기록하지 않는다. */
    fun masked(): String = if (value.length <= MASK_PREFIX) "****" else value.take(MASK_PREFIX) + "****"

    override fun toString(): String = "AccessToken(${masked()}, expiresAt=$expiresAt)"

    companion object {
        private const val MASK_PREFIX = 6

        fun issuedAt(value: String, issuedAt: Instant, expiresIn: Duration): AccessToken =
            AccessToken(value, issuedAt, issuedAt.plus(expiresIn))
    }
}
