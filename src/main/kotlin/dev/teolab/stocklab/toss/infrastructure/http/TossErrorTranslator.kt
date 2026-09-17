package dev.teolab.stocklab.toss.infrastructure.http

import dev.teolab.stocklab.toss.domain.TossApiException
import dev.teolab.stocklab.toss.domain.TossAuthException
import dev.teolab.stocklab.toss.domain.TossClientException
import dev.teolab.stocklab.toss.domain.TossErrorCode
import dev.teolab.stocklab.toss.domain.TossForbiddenException
import dev.teolab.stocklab.toss.domain.TossRateLimitException
import dev.teolab.stocklab.toss.domain.TossServerException
import dev.teolab.stocklab.toss.domain.TossStockNotFoundException
import dev.teolab.stocklab.toss.domain.TossUnsupportedMarketException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpResponse
import java.time.Duration

/** 에러 응답을 도메인 예외로 바꾼다. RestClient 의 statusHandler 에서 사용한다. */
class TossErrorTranslator(
    private val errorReader: TossErrorReader = TossErrorReader(),
) {
    fun translate(request: HttpRequest, response: ClientHttpResponse): TossApiException {
        val status = response.statusCode
        val body = errorReader.read(response)
        // requestId 는 토스 형식 본문에만 있다. OAuth2 표준 에러나 빈 본문일 때는 헤더에서 주워온다.
        val requestId = body?.requestId ?: response.headers.getFirst(REQUEST_ID_HEADER)
        val code = TossErrorCode.from(body?.code)
        val detail = buildString {
            append(request.method).append(' ').append(request.uri.path)
            append(" 실패: status=").append(status.value())
            body?.code?.let { append(", code=").append(it) }
            body?.message?.let { append(", message=").append(it) }
        }

        return when {
            code == TossErrorCode.STOCK_NOT_FOUND -> TossStockNotFoundException(detail, requestId)
            code == TossErrorCode.UNSUPPORTED_MARKET -> TossUnsupportedMarketException(detail, requestId)
            code == TossErrorCode.RATE_LIMIT_EXCEEDED || status.value() == TOO_MANY_REQUESTS ->
                TossRateLimitException(detail, retryAfter(response.headers), requestId)

            code.isAuthFailure || status.value() == UNAUTHORIZED -> TossAuthException(detail, code, requestId)
            status.value() == FORBIDDEN -> TossForbiddenException(
                "$detail — 토스증권 WTS 의 Open API 허용 IP 에 현재 공인 IP 가 등록되어 있는지 확인하세요",
                requestId,
            )

            status.is5xxServerError -> TossServerException(detail, requestId)
            else -> TossClientException(detail, code, requestId)
        }
    }

    /** Retry-After 는 초 단위 정수 또는 HTTP-date 로 온다. 둘 다 받아준다. */
    private fun retryAfter(headers: HttpHeaders): Duration? {
        val raw = headers.getFirst(HttpHeaders.RETRY_AFTER)?.trim() ?: return null
        raw.toLongOrNull()?.let { return Duration.ofSeconds(it) }
        val epochMillis = runCatching { headers.getFirstDate(HttpHeaders.RETRY_AFTER) }.getOrNull() ?: return null
        if (epochMillis < 0) return null
        return Duration.ofMillis(epochMillis - System.currentTimeMillis()).takeIf { !it.isNegative }
    }

    companion object {
        const val REQUEST_ID_HEADER = "x-request-id"
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val TOO_MANY_REQUESTS = 429
    }
}
