package dev.teolab.stocklab.toss.domain

import java.time.Duration

/**
 * 토스 오픈API 호출 실패.
 *
 * [requestId] 는 토스에 문의할 때 쓸 수 있는 유일한 단서이므로 가능한 한 항상 담는다.
 */
sealed class TossApiException(
    detail: String,
    val errorCode: TossErrorCode,
    val requestId: String?,
    cause: Throwable? = null,
) : RuntimeException(compose(detail, requestId), cause)

private fun compose(detail: String, requestId: String?): String =
    if (requestId.isNullOrBlank()) detail else "$detail (requestId=$requestId)"

/** 401 또는 invalid-token / expired-token / token-revoked. 재발급 후 1회 재시도까지 해본 뒤의 실패다. */
class TossAuthException(
    detail: String,
    errorCode: TossErrorCode = TossErrorCode.INVALID_TOKEN,
    requestId: String? = null,
    cause: Throwable? = null,
) : TossApiException(detail, errorCode, requestId, cause)

/** 허용 IP 미등록 등으로 인한 403. 1단계에서 가장 자주 만나는 실패라 따로 둔다. */
class TossForbiddenException(
    detail: String,
    requestId: String? = null,
) : TossApiException(detail, TossErrorCode.UNKNOWN, requestId)

/** 429 또는 rate-limit-exceeded. [retryAfter] 가 있으면 그 시간만큼 기다린 뒤 재시도한다. */
class TossRateLimitException(
    detail: String,
    val retryAfter: Duration?,
    requestId: String? = null,
) : TossApiException(detail, TossErrorCode.RATE_LIMIT_EXCEEDED, requestId)

/** 존재하지 않는 종목. 수집 루프에서는 건너뛰고 진행한다. */
class TossStockNotFoundException(
    detail: String,
    requestId: String? = null,
) : TossApiException(detail, TossErrorCode.STOCK_NOT_FOUND, requestId)

/** 국내 전용 API 에 해외 종목을 요청한 경우. 재시도해도 소용없다. */
class TossUnsupportedMarketException(
    detail: String,
    requestId: String? = null,
) : TossApiException(detail, TossErrorCode.UNSUPPORTED_MARKET, requestId)

/** 5xx 또는 해석할 수 없는 응답. 재시도 대상이다. */
class TossServerException(
    detail: String,
    requestId: String? = null,
    cause: Throwable? = null,
) : TossApiException(detail, TossErrorCode.UNKNOWN, requestId, cause)

/** 위 어디에도 해당하지 않는 4xx. */
class TossClientException(
    detail: String,
    errorCode: TossErrorCode = TossErrorCode.UNKNOWN,
    requestId: String? = null,
) : TossApiException(detail, errorCode, requestId)
