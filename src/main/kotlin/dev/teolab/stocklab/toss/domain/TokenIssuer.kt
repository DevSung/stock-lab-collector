package dev.teolab.stocklab.toss.domain

/**
 * 토큰 발급 포트. 구현은 infrastructure 에 둔다.
 *
 * 이 포트를 호출하면 **이전 토큰이 무효화된다.** 호출 지점을 늘리지 말 것.
 */
fun interface TokenIssuer {
    fun issue(): AccessToken
}
