package dev.teolab.stocklab.toss.application

import dev.teolab.stocklab.toss.domain.AccessToken
import dev.teolab.stocklab.toss.domain.TokenIssuer
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 애플리케이션 전체에서 **유일한** 토큰 발급 지점.
 *
 * 토스 토큰은 클라이언트당 1개만 유효하고 재발급이 곧 이전 토큰의 무효화이므로,
 * 여기 말고 다른 곳에서 [TokenIssuer] 를 부르면 서로의 토큰을 죽인다.
 *
 * 설계 요점
 * - single-flight: 이중 검사 잠금으로 동시 호출이 몰려도 발급은 1회만 일어난다.
 * - 선제 갱신: 만료 [expiryMargin] 전부터 새 토큰으로 갈아탄다. 별도 스케줄러는 두지 않는다.
 *   유휴 상태에서 주기적으로 갈아치우면 재발급이 곧 무효화라 손해만 본다.
 * - CAS 재발급: [refreshIfSame] 은 실패에 쓰인 토큰이 아직 캐시에 있을 때만 재발급한다.
 *   무조건 재발급하면 동시에 401 을 받은 요청들이 서로를 revoke 하는 자멸 루프가 생긴다.
 */
class TossTokenManager(
    private val issuer: TokenIssuer,
    private val clock: Clock,
    private val expiryMargin: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = ReentrantLock()
    private val issueCounter = AtomicInteger()

    @Volatile
    private var cached: AccessToken? = null

    /** 지금 사용 가능한 토큰 값. 없거나 만료가 임박했으면 발급해서 돌려준다. */
    fun accessToken(): String {
        usableToken()?.let { return it.value }
        return lock.withLock {
            // 잠금을 기다리는 동안 다른 스레드가 이미 발급했을 수 있으므로 반드시 재검사한다.
            usableToken()?.value ?: issueAndCache().value
        }
    }

    /**
     * 인증 실패에 사용된 [staleToken] 이 아직 캐시에 남아 있을 때만 재발급한다.
     * 이미 다른 스레드가 갈아끼웠다면 그 토큰을 그대로 돌려준다.
     */
    fun refreshIfSame(staleToken: String): String = lock.withLock {
        val current = cached
        if (current != null && current.value != staleToken) {
            log.debug("이미 다른 스레드가 토큰을 갱신했다. 재발급을 건너뛴다: {}", current.masked())
            current.value
        } else {
            issueAndCache().value
        }
    }

    /** 현재 캐시된 토큰. 발급을 유발하지 않는다. */
    fun currentToken(): AccessToken? = cached

    /** 지금까지 실제로 발급한 횟수. 토큰이 몇 번 갈렸는지 확인용. */
    fun issueCount(): Int = issueCounter.get()

    private fun usableToken(): AccessToken? = cached?.takeIf { it.isUsableAt(clock.instant(), expiryMargin) }

    private fun issueAndCache(): AccessToken {
        val token = issuer.issue()
        cached = token
        val count = issueCounter.incrementAndGet()
        log.info("토큰 발급 완료: {} (만료 {}, 누적 발급 {}회)", token.masked(), token.expiresAt, count)
        return token
    }
}
