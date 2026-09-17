package dev.teolab.stocklab.toss.infrastructure.http

import dev.teolab.stocklab.toss.application.TossTokenManager
import dev.teolab.stocklab.toss.domain.TossErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/**
 * 모든 API 호출에 Bearer 토큰을 붙이고, 인증 실패면 **딱 1회** 재발급 후 재시도한다.
 *
 * 주의 1 — 이 인터셉터는 반드시 **체인의 가장 안쪽**(마지막에 등록)이어야 한다.
 *   Spring 의 `InterceptingRequestExecution` 은 인터셉터 iterator 를 공유하므로,
 *   안쪽이 아닌 위치에서 execute 를 두 번 부르면 뒤따르는 인터셉터가 조용히 건너뛰어진다.
 *   (5단계에서 rate limiter 를 추가할 때 이 순서를 지킬 것)
 *
 * 주의 2 — 토큰 엔드포인트에는 절대 붙지 않아야 한다. 무한 재귀를 막는 3중 방어 중 마지막 선이다.
 *   1) 토큰 발급은 인터셉터가 없는 별도 RestClient(`tossAuthRestClient`)로만 한다
 *   2) 이 인터셉터를 `RestClientCustomizer` 빈으로 등록하지 않는다
 *   3) 아래 경로 가드
 */
class BearerTokenInterceptor(
    private val tokenManager: TossTokenManager,
    private val errorReader: TossErrorReader = TossErrorReader(),
) : ClientHttpRequestInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        if (request.uri.path.startsWith(OAUTH_PATH_PREFIX)) {
            return execution.execute(request, body)
        }

        val usedToken = tokenManager.accessToken()
        request.headers.setBearerAuth(usedToken)
        val response = execution.execute(request, body)
        if (!isAuthFailure(response)) return response

        log.warn("인증 실패 응답(status={}) — 토큰을 재발급하고 1회만 재시도한다", response.statusCode.value())
        response.close()

        val refreshed = tokenManager.refreshIfSame(usedToken)
        request.headers.setBearerAuth(refreshed)
        return execution.execute(request, body)
    }

    private fun isAuthFailure(response: ClientHttpResponse): Boolean {
        val status = response.statusCode
        if (status.value() == UNAUTHORIZED) return true
        if (!status.is4xxClientError) return false
        return TossErrorCode.from(errorReader.read(response)?.code).isAuthFailure
    }

    companion object {
        const val OAUTH_PATH_PREFIX = "/oauth2/"
        private const val UNAUTHORIZED = 401
    }
}
