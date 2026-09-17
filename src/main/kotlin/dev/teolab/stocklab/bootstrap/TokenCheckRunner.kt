package dev.teolab.stocklab.bootstrap

import dev.teolab.stocklab.toss.application.TossTokenManager
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 1단계 검증용. 토큰이 실제로 발급되는지만 확인하고 종료한다.
 *
 *   ./scripts/run.sh --spring.profiles.active=tokencheck
 *
 * 토큰 원문은 어떤 경우에도 로그에 남기지 않는다.
 */
@Component
@Profile("tokencheck & !test")  // 테스트 컨텍스트에서는 실행되지 않게 한다
@Order(1)
class TokenCheckRunner(
    private val tokenManager: TossTokenManager,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        tokenManager.accessToken()
        val token = checkNotNull(tokenManager.currentToken()) { "토큰 발급 직후인데 캐시가 비어 있다" }
        log.info(
            "토큰 발급 성공: {} / 발급 {} / 만료 {}",
            token.masked(),
            token.issuedAt,
            token.expiresAt,
        )
    }
}
