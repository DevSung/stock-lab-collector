package dev.teolab.stocklab.toss.infrastructure.auth

import dev.teolab.stocklab.toss.domain.TossAuthException
import dev.teolab.stocklab.toss.infrastructure.config.TossProperties
import dev.teolab.stocklab.toss.infrastructure.http.TossErrorTranslator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class TossAuthClientTest {

    private val baseUrl = "https://openapi.example.invalid"
    private val now = Instant.parse("2026-09-17T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = TossProperties(
        baseUrl = baseUrl,
        clientId = "test-client-id",
        clientSecret = "test-client-secret",
    )

    private val builder = RestClient.builder().baseUrl(baseUrl)
    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    private val restClient = builder
        .defaultStatusHandler({ it.isError }) { request, response ->
            throw TossErrorTranslator().translate(request, response)
        }
        .build()
    private val authClient = TossAuthClient(restClient, properties, clock)

    @Test
    fun `client_credentials 를 form 으로 보내고 토큰을 만든다`() {
        server.expect(requestTo("$baseUrl/oauth2/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(
                content().formDataContains(
                    mapOf(
                        "grant_type" to "client_credentials",
                        "client_id" to "test-client-id",
                        "client_secret" to "test-client-secret",
                    ),
                ),
            )
            .andRespond(
                withSuccess(
                    """{"access_token":"issued-token-value","token_type":"Bearer","expires_in":3600}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val token = authClient.issue()

        assertEquals("issued-token-value", token.value)
        assertEquals(now, token.issuedAt)
        assertEquals(now.plus(Duration.ofHours(1)), token.expiresAt)
        server.verify()
    }

    @Test
    fun `자격증명이 틀리면 인증 예외로 바뀐다`() {
        server.expect(requestTo("$baseUrl/oauth2/token"))
            .andRespond(
                withStatus(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"requestId":"req-9","code":"invalid-token","message":"bad credentials"}}"""),
            )

        val ex = assertThrows<TossAuthException> { authClient.issue() }

        assertEquals("req-9", ex.requestId)
        server.verify()
    }
}
