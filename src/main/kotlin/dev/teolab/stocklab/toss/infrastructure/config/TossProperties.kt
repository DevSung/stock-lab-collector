package dev.teolab.stocklab.toss.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 토스 오픈API 설정.
 *
 * [clientId] / [clientSecret] 에는 기본값을 두지 않는다.
 * 환경변수가 없으면 플레이스홀더 해석에 실패해 기동 자체가 멈춰야 한다.
 */
@ConfigurationProperties(prefix = "toss")
data class TossProperties(
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String,
    val auth: Auth = Auth(),
    val http: Http = Http(),
) {
    data class Auth(
        /** 기동 시 토큰을 미리 발급할지. 테스트에서는 반드시 false 여야 한다. */
        val eagerInit: Boolean = true,
        /** 만료 이 시간 전부터 새 토큰으로 갈아탄다. */
        val expiryMargin: Duration = Duration.ofMinutes(5),
    )

    data class Http(
        val connectTimeout: Duration = Duration.ofSeconds(3),
        val readTimeout: Duration = Duration.ofSeconds(10),
    )
}
