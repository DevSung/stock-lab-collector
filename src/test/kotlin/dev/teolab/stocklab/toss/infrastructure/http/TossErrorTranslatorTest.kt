package dev.teolab.stocklab.toss.infrastructure.http

import dev.teolab.stocklab.toss.domain.TossAuthException
import dev.teolab.stocklab.toss.domain.TossForbiddenException
import dev.teolab.stocklab.toss.domain.TossRateLimitException
import dev.teolab.stocklab.toss.domain.TossServerException
import dev.teolab.stocklab.toss.domain.TossStockNotFoundException
import dev.teolab.stocklab.toss.domain.TossUnsupportedMarketException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import java.net.URI
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TossErrorTranslatorTest {

    private val translator = TossErrorTranslator()
    private val request: HttpRequest =
        MockClientHttpRequest(HttpMethod.GET, URI.create("https://openapi.tossinvest.com/api/v1/candles"))

    private fun errorBody(code: String, message: String = "메시지", requestId: String = "req-1") =
        """{"error":{"requestId":"$requestId","code":"$code","message":"$message"}}"""

    private fun response(status: HttpStatus, body: String, headers: HttpHeaders = HttpHeaders()) =
        MockClientHttpResponse(body.toByteArray(), status).apply { this.headers.addAll(headers) }

    @Test
    fun `토큰 관련 코드는 인증 예외로 바뀐다`() {
        listOf("invalid-token", "expired-token", "token-revoked").forEach { code ->
            val ex = translator.translate(request, response(HttpStatus.UNAUTHORIZED, errorBody(code)))
            assertIs<TossAuthException>(ex, "code=$code")
            assertEquals("req-1", ex.requestId)
        }
    }

    @Test
    fun `429 는 Retry-After 를 초 단위로 읽는다`() {
        val headers = HttpHeaders().apply { add(HttpHeaders.RETRY_AFTER, "7") }
        val ex = translator.translate(
            request,
            response(HttpStatus.TOO_MANY_REQUESTS, errorBody("rate-limit-exceeded"), headers),
        )

        assertIs<TossRateLimitException>(ex)
        assertEquals(Duration.ofSeconds(7), ex.retryAfter)
    }

    @Test
    fun `Retry-After 가 없으면 null 이고 예외 종류는 유지된다`() {
        val ex = translator.translate(request, response(HttpStatus.TOO_MANY_REQUESTS, ""))

        assertIs<TossRateLimitException>(ex)
        assertNull(ex.retryAfter)
    }

    @Test
    fun `종목 없음과 미지원 마켓은 재시도 대상이 아닌 별도 예외다`() {
        assertIs<TossStockNotFoundException>(
            translator.translate(request, response(HttpStatus.NOT_FOUND, errorBody("stock-not-found"))),
        )
        assertIs<TossUnsupportedMarketException>(
            translator.translate(request, response(HttpStatus.BAD_REQUEST, errorBody("unsupported-market"))),
        )
    }

    @Test
    fun `403 은 허용 IP 확인 안내를 메시지에 담는다`() {
        val ex = translator.translate(request, response(HttpStatus.FORBIDDEN, ""))

        assertIs<TossForbiddenException>(ex)
        assertContains(ex.message!!, "허용 IP")
    }

    @Test
    fun `5xx 와 파싱 불가 본문은 서버 예외로 떨어진다`() {
        val ex = translator.translate(request, response(HttpStatus.BAD_GATEWAY, "<html>nginx</html>"))

        assertIs<TossServerException>(ex)
        assertNull(ex.requestId)
    }

    @Test
    fun `메시지에 요청 경로와 상태코드가 남는다`() {
        val ex = translator.translate(request, response(HttpStatus.NOT_FOUND, errorBody("stock-not-found")))

        assertContains(ex.message!!, "/api/v1/candles")
        assertContains(ex.message!!, "404")
        assertContains(ex.message!!, "requestId=req-1")
    }

    @Test
    fun `토큰 엔드포인트의 OAuth2 표준 에러 형식도 읽는다`() {
        // 실측: POST /oauth2/token 은 토스 형식이 아니라 OAuth2 표준 형식으로 답한다
        val oauthError = """{"error":"invalid_client","error_description":"Client authentication failed: client_id"}"""
        val headers = HttpHeaders().apply { add(TossErrorTranslator.REQUEST_ID_HEADER, "hBtmrZM2Rr3AFkQU") }

        val ex = translator.translate(request, response(HttpStatus.UNAUTHORIZED, oauthError, headers))

        assertIs<TossAuthException>(ex)
        assertContains(ex.message!!, "invalid_client")
        assertContains(ex.message!!, "Client authentication failed")
    }

    @Test
    fun `본문에 requestId 가 없으면 x-request-id 헤더에서 가져온다`() {
        val headers = HttpHeaders().apply { add(TossErrorTranslator.REQUEST_ID_HEADER, "hdr-req-1") }

        val ex = translator.translate(request, response(HttpStatus.BAD_GATEWAY, "", headers))

        assertEquals("hdr-req-1", ex.requestId)
    }

    @Test
    fun `본문의 requestId 가 헤더보다 우선한다`() {
        val headers = HttpHeaders().apply { add(TossErrorTranslator.REQUEST_ID_HEADER, "hdr-req-1") }

        val ex = translator.translate(request, response(HttpStatus.NOT_FOUND, errorBody("stock-not-found"), headers))

        assertEquals("req-1", ex.requestId)
    }
}
