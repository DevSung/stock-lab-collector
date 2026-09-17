package dev.teolab.stocklab.toss.infrastructure.auth

import dev.teolab.stocklab.toss.domain.AccessToken
import dev.teolab.stocklab.toss.domain.TokenIssuer
import dev.teolab.stocklab.toss.domain.TossAuthException
import dev.teolab.stocklab.toss.infrastructure.config.TossProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration

/**
 * [TokenIssuer] 구현. 인터셉터가 붙지 않은 [authRestClient] 로만 호출해 무한 재귀를 막는다.
 *
 * 이 클래스는 API 용 RestClient 를 **절대 참조하지 않는다.** 순환 의존이자 재귀의 시작점이 된다.
 */
class TossAuthClient(
    private val authRestClient: RestClient,
    private val properties: TossProperties,
    private val clock: Clock,
) : TokenIssuer {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun issue(): AccessToken {
        log.debug("토큰 발급 요청 — 이 시점에 기존 토큰은 무효화된다")

        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", GRANT_TYPE)
            add("client_id", properties.clientId)
            add("client_secret", properties.clientSecret)
        }

        val response = authRestClient.post()
            .uri(TOKEN_PATH)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse::class.java)
            ?: throw TossAuthException("토큰 응답 본문이 비어 있다")

        return AccessToken.issuedAt(
            value = response.accessToken,
            issuedAt = clock.instant(),
            expiresIn = Duration.ofSeconds(response.expiresIn),
        )
    }

    companion object {
        const val TOKEN_PATH = "/oauth2/token"
        private const val GRANT_TYPE = "client_credentials"
    }
}
