package dev.teolab.stocklab.toss.infrastructure.runner

import dev.teolab.stocklab.toss.application.TossTokenManager
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 기동 시 토큰을 한 번 발급해 자격증명·허용 IP 오류를 즉시 드러낸다.
 * 실패하면 예외를 그대로 던져 기동을 중단시킨다(fail-fast).
 *
 * 테스트에서는 `toss.auth.eager-init: false` 로 반드시 꺼야 한다.
 * 컨텍스트를 띄울 때마다 토큰을 발급하면 실행 중인 배치의 토큰이 죽는다.
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "toss.auth", name = ["eager-init"], havingValue = "true", matchIfMissing = true)
class TossTokenWarmUpRunner(
    private val tokenManager: TossTokenManager,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val token = tokenManager.accessToken()
        log.info("토큰 준비 완료 (길이 {}자). 상세는 tokencheck 프로파일로 확인할 것", token.length)
    }
}
