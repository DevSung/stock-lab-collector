package dev.teolab.stocklab.toss.infrastructure.http

import org.slf4j.LoggerFactory
import org.springframework.http.client.ClientHttpResponse
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * 에러 응답 본문을 읽어 [TossErrorBody] 로 정규화한다. 두 가지 형식이 온다.
 *
 * - `/api/v1` 이하 : 토스 형식 `{ "error": { requestId, code, message, data } }`
 * - `/oauth2/token` : OAuth2 표준 형식 `{ "error": "invalid_client", "error_description": "..." }`
 * - 429 : 래퍼 없이 평평한 형식 `{ "requestId": ..., "code": "rate-limit-exceeded", "message": ... }`
 *
 * 응답 본문은 한 번만 읽을 수 있는 것이 기본이므로, 이 클래스를 쓰는 RestClient 는
 * 반드시 `BufferingClientHttpRequestFactory` 로 감싸야 한다.
 * 그러지 않으면 인터셉터가 먼저 읽어버린 뒤 statusHandler 가 빈 본문을 보게 된다.
 */
class TossErrorReader(
    private val mapper: JsonMapper = JsonMapper.builder().build(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 파싱에 실패하면 null. 에러 처리 경로에서 또 예외를 던지지 않는다. */
    fun read(response: ClientHttpResponse): TossErrorBody? {
        val raw = runCatching { response.body.readBytes() }.getOrElse {
            log.debug("에러 본문을 읽지 못했다", it)
            return null
        }
        if (raw.isEmpty()) return null

        val root = runCatching { mapper.readTree(raw) }.getOrElse {
            log.debug("에러 본문이 JSON 이 아니다: {}", raw.decodeToString().take(ERROR_BODY_LOG_LIMIT))
            return null
        }
        val error = root.get("error")
            // 429 는 error 래퍼 없이 최상위에 바로 code 가 온다
            ?: return if (root.path("code").isString) {
                TossErrorBody(
                    requestId = root.path("requestId").text(),
                    code = root.path("code").text(),
                    message = root.path("message").text(),
                )
            } else {
                null
            }

        return when {
            error.isObject -> TossErrorBody(
                requestId = error.path("requestId").text(),
                code = error.path("code").text(),
                message = error.path("message").text(),
            )

            error.isString -> TossErrorBody(
                code = error.text(),
                message = root.path("error_description").text(),
            )

            else -> null
        }
    }

    private fun JsonNode.text(): String? = if (isString) stringValue() else null

    companion object {
        const val ERROR_BODY_LOG_LIMIT = 512
    }
}
