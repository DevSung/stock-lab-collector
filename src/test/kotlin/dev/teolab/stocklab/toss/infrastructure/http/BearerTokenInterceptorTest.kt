package dev.teolab.stocklab.toss.infrastructure.http

import dev.teolab.stocklab.toss.application.TossTokenManager
import dev.teolab.stocklab.toss.domain.AccessToken
import dev.teolab.stocklab.toss.domain.TokenIssuer
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class BearerTokenInterceptorTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-17T09:00:00Z"), ZoneOffset.UTC)
    private val counter = AtomicInteger()
    private val tokenManager = TossTokenManager(
        TokenIssuer { AccessToken.issuedAt("token-${counter.incrementAndGet()}", clock.instant(), Duration.ofHours(1)) },
        clock,
        Duration.ofMinutes(5),
    )
    private val interceptor = BearerTokenInterceptor(tokenManager)

    /** 미리 준비한 응답을 순서대로 돌려주면서 각 시도의 Authorization 헤더를 기록한다. */
    private class RecordingExecution(responses: List<ClientHttpResponse>) : ClientHttpRequestExecution {
        private val queue = ArrayDeque(responses)
        val authorizations = mutableListOf<String?>()

        override fun execute(request: HttpRequest, body: ByteArray): ClientHttpResponse {
            authorizations += request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            return queue.removeFirst()
        }
    }

    private fun request(path: String): HttpRequest =
        MockClientHttpRequest(HttpMethod.GET, URI.create("https://openapi.tossinvest.com$path"))

    private fun response(status: HttpStatus, body: String = "") =
        MockClientHttpResponse(body.toByteArray(), status)

    @Test
    fun `정상 응답이면 토큰을 붙이고 그대로 반환한다`() {
        val execution = RecordingExecution(listOf(response(HttpStatus.OK, "{}")))

        val result = interceptor.intercept(request("/api/v1/candles"), ByteArray(0), execution)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(listOf<String?>("Bearer token-1"), execution.authorizations)
        assertEquals(1, tokenManager.issueCount())
    }

    @Test
    fun `401 을 받으면 재발급 후 딱 한 번 재시도한다`() {
        val execution = RecordingExecution(
            listOf(response(HttpStatus.UNAUTHORIZED), response(HttpStatus.OK, "{}")),
        )

        val result = interceptor.intercept(request("/api/v1/candles"), ByteArray(0), execution)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(listOf<String?>("Bearer token-1", "Bearer token-2"), execution.authorizations)
        assertEquals(2, tokenManager.issueCount())
    }

    @Test
    fun `본문의 token-revoked 코드도 인증 실패로 보고 재시도한다`() {
        val revoked = """{"error":{"requestId":"req-1","code":"token-revoked","message":"revoked"}}"""
        val execution = RecordingExecution(
            listOf(response(HttpStatus.BAD_REQUEST, revoked), response(HttpStatus.OK, "{}")),
        )

        val result = interceptor.intercept(request("/api/v1/candles"), ByteArray(0), execution)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(2, execution.authorizations.size)
    }

    @Test
    fun `인증과 무관한 4xx 는 재시도하지 않는다`() {
        val notFound = """{"error":{"requestId":"req-2","code":"stock-not-found","message":"없음"}}"""
        val execution = RecordingExecution(listOf(response(HttpStatus.NOT_FOUND, notFound)))

        val result = interceptor.intercept(request("/api/v1/candles"), ByteArray(0), execution)

        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
        assertEquals(1, execution.authorizations.size)
        assertEquals(1, tokenManager.issueCount())
    }

    @Test
    fun `재시도한 응답도 실패면 세 번째 시도는 하지 않는다`() {
        val execution = RecordingExecution(
            listOf(response(HttpStatus.UNAUTHORIZED), response(HttpStatus.UNAUTHORIZED)),
        )

        val result = interceptor.intercept(request("/api/v1/candles"), ByteArray(0), execution)

        assertEquals(HttpStatus.UNAUTHORIZED, result.statusCode)
        assertEquals(2, execution.authorizations.size)
    }

    @Test
    fun `토큰 엔드포인트는 가로채지 않는다 - 무한 재귀 방어선`() {
        val execution = RecordingExecution(listOf(response(HttpStatus.OK, "{}")))

        interceptor.intercept(request("/oauth2/token"), ByteArray(0), execution)

        assertEquals(listOf<String?>(null), execution.authorizations)
        assertEquals(0, tokenManager.issueCount(), "토큰 발급 요청이 토큰을 요구하면 재귀가 된다")
    }
}
