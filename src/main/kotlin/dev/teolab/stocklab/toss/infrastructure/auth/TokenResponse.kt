package dev.teolab.stocklab.toss.infrastructure.auth

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * POST /oauth2/token 응답 (OAuth2 client_credentials 표준 형식).
 *
 * @JsonCreator 를 명시해 Kotlin 모듈이 등록되지 않은 매퍼에서도 역직렬화되게 한다.
 */
data class TokenResponse @JsonCreator constructor(
    @param:JsonProperty("access_token") val accessToken: String,
    @param:JsonProperty("token_type") val tokenType: String? = null,
    @param:JsonProperty("expires_in") val expiresIn: Long,
)
