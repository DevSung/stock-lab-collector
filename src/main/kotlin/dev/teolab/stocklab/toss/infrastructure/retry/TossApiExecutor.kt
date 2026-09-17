package dev.teolab.stocklab.toss.infrastructure.retry

import dev.teolab.stocklab.toss.domain.TossApiException
import dev.teolab.stocklab.toss.domain.TossRateLimitException
import dev.teolab.stocklab.toss.domain.TossServerException
import dev.teolab.stocklab.toss.infrastructure.ratelimit.ApiGroup
import dev.teolab.stocklab.toss.infrastructure.ratelimit.GroupRateLimiter
import dev.teolab.stocklab.toss.infrastructure.ratelimit.Sleeper
import org.slf4j.LoggerFactory
import org.springframework.web.client.ResourceAccessException
import java.time.Clock
import java.time.Duration
import kotlin.random.Random

/**
 * 모든 토스 API 호출이 지나는 문.
 *
 * 순서가 중요하다. **재시도 루프가 rate limiter 바깥**에 있어서 재시도할 때마다 퍼밋을 다시 얻는다.
 * 반대로 두면 429 후의 재시도가 한도 회계에서 빠져 429 를 더 유발한다.
 *
 * 인증 실패 재시도는 여기가 아니라 BearerTokenInterceptor 안에서 이미 1회 처리된다.
 * 거기까지 하고도 실패한 인증 오류는 재시도해봐야 토큰만 더 태우므로 여기서는 대상이 아니다.
 */
class TossApiExecutor(
    private val rateLimiter: GroupRateLimiter,
    private val sleeper: Sleeper,
    private val clock: Clock,
    private val policy: RetryPolicy = RetryPolicy(),
    private val random: Random = Random.Default,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T> execute(group: ApiGroup, call: () -> T): T {
        var attempt = 1
        while (true) {
            rateLimiter.acquire(group)
            try {
                return call()
            } catch (e: Exception) {
                val delay = retryDelayOrNull(e, attempt)
                if (delay == null || attempt >= policy.maxAttempts) throw e

                if (e is TossRateLimitException) {
                    // 재시도하는 나만 기다려선 부족하다. 같은 그룹의 다른 호출도 막아야 한다.
                    rateLimiter.penalize(group, clock.instant().plus(delay))
                }
                log.warn(
                    "{} 호출 실패({}/{}) — {} 후 재시도: {}",
                    group, attempt, policy.maxAttempts, delay, e.message,
                )
                sleeper.sleep(delay)
                attempt++
            }
        }
    }

    /** 재시도할 가치가 있으면 대기 시간을, 아니면 null 을 준다. */
    private fun retryDelayOrNull(e: Exception, attempt: Int): Duration? = when (e) {
        is TossRateLimitException -> {
            val retryAfter = e.retryAfter
            when {
                // 서버가 알려준 대기 시간이 우선이다. 다만 너무 길면 포기하는 게 낫다.
                retryAfter == null -> jittered(policy.backoffFor(attempt))
                retryAfter > policy.retryAfterCap -> null
                else -> jittered(retryAfter)
            }
        }

        is TossServerException -> jittered(policy.backoffFor(attempt))
        is ResourceAccessException -> jittered(policy.backoffFor(attempt)) // 타임아웃·연결 실패
        is TossApiException -> null // 종목 없음, 미지원 마켓, 인증 실패 등 — 다시 해도 같다
        else -> null
    }

    /**
     * 백오프에 흔들림을 준다.
     *
     * 여러 종목을 연달아 돌리다 429 를 만나면 재시도 시각이 똑같이 겹쳐서 다시 429 가 난다.
     * ±[RetryPolicy.jitterRatio] 만큼 흩어놓는다.
     */
    private fun jittered(base: Duration): Duration {
        if (policy.jitterRatio == 0.0) return base
        val spread = base.toMillis() * policy.jitterRatio
        val offset = (random.nextDouble() * 2 - 1) * spread
        return Duration.ofMillis((base.toMillis() + offset).toLong().coerceAtLeast(0))
    }
}
