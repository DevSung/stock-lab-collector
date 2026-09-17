package dev.teolab.stocklab.toss.domain

/** 토스 오픈API 가 error.code 로 내려주는 값. 문서에 없는 코드는 [UNKNOWN] 으로 떨어진다. */
enum class TossErrorCode(val code: String) {
    INVALID_TOKEN("invalid-token"),
    EXPIRED_TOKEN("expired-token"),
    TOKEN_REVOKED("token-revoked"),
    RATE_LIMIT_EXCEEDED("rate-limit-exceeded"),
    STOCK_NOT_FOUND("stock-not-found"),
    UNSUPPORTED_MARKET("unsupported-market"),
    UNKNOWN("unknown"),
    ;

    /** 토큰을 새로 발급하면 해소될 수 있는 실패인지. */
    val isAuthFailure: Boolean
        get() = this == INVALID_TOKEN || this == EXPIRED_TOKEN || this == TOKEN_REVOKED

    companion object {
        fun from(code: String?): TossErrorCode = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
