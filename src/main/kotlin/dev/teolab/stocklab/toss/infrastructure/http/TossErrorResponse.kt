package dev.teolab.stocklab.toss.infrastructure.http

/** 토스 오픈API 의 공통 에러 응답: `{ "error": { requestId, code, message, data } }` */
data class TossErrorResponse(
    val error: TossErrorBody? = null,
)

data class TossErrorBody(
    val requestId: String? = null,
    val code: String? = null,
    val message: String? = null,
    val data: Map<String, Any?>? = null,
)
