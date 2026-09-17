package dev.teolab.stocklab.toss.infrastructure.client

import dev.teolab.stocklab.market.domain.DailyCandleReader
import dev.teolab.stocklab.toss.infrastructure.ratelimit.FakeSleeper
import dev.teolab.stocklab.toss.infrastructure.ratelimit.GroupRateLimiter
import dev.teolab.stocklab.toss.infrastructure.ratelimit.MutableClock
import dev.teolab.stocklab.toss.infrastructure.retry.RetryPolicy
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.util.DefaultUriBuilderFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TossCandleClientTest {

    private val baseUrl = "https://openapi.example.invalid"
    private var capturedUri: String? = null

    // 운영과 같은 인코딩 모드로 맞춘다. 이게 다르면 %2B 검증이 의미가 없다.
    private val builder = RestClient.builder().uriBuilderFactory(
        DefaultUriBuilderFactory(baseUrl).apply {
            encodingMode = DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY
        },
    )
    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    // 실제로 재우지 않는 executor. 이 테스트의 관심사는 URI 와 응답 매핑이다.
    private val clock = MutableClock(Instant.parse("2026-09-17T09:00:00Z"))
    private val executor = TossApiExecutor(
        GroupRateLimiter(clock, FakeSleeper(clock)),
        FakeSleeper(clock),
        clock,
        RetryPolicy(maxAttempts = 1),
    )
    private val client = TossCandleClient(builder.build(), executor)

    private val captureUri = RequestMatcher { request: ClientHttpRequest ->
        capturedUri = request.uri.toString()
    }

    /** 실제 API 응답을 그대로 옮긴 것. 가격과 거래량이 문자열인 점이 핵심이다. */
    private val realPayload = """
        {
          "result": {
            "candles": [
              {
                "timestamp": "2026-09-17T00:00:00.000+09:00",
                "openPrice": "251500",
                "highPrice": "259000",
                "lowPrice": "251000",
                "closePrice": "256000",
                "volume": "11058213",
                "currency": "KRW"
              },
              {
                "timestamp": "2026-09-16T00:00:00.000+09:00",
                "openPrice": "250000",
                "highPrice": "254000",
                "lowPrice": "247500",
                "closePrice": "253500",
                "volume": "16705728",
                "currency": "KRW"
              }
            ],
            "nextBefore": "2026-09-15T00:00:00.000+09:00"
          }
        }
    """.trimIndent()

    @Test
    fun `문자열로 오는 가격과 거래량을 도메인 타입으로 옮긴다`() {
        server.expect(captureUri).andRespond(withSuccess(realPayload, MediaType.APPLICATION_JSON))

        val page = client.read("005930", count = 2)

        assertEquals(2, page.candles.size)
        val newest = page.candles.first()
        assertEquals("005930", newest.symbol)
        assertEquals(LocalDate.of(2026, 9, 17), newest.tradeDate)
        assertEquals(BigDecimal("251500"), newest.open)
        assertEquals(BigDecimal("259000"), newest.high)
        assertEquals(BigDecimal("251000"), newest.low)
        assertEquals(BigDecimal("256000"), newest.close)
        assertEquals(11_058_213L, newest.volume)
        assertEquals("KRW", newest.currency)
        assertTrue(newest.adjusted)
        server.verify()
    }

    @Test
    fun `봉은 최신순이고 nextBefore 로 다음 페이지를 가리킨다`() {
        server.expect(captureUri).andRespond(withSuccess(realPayload, MediaType.APPLICATION_JSON))

        val page = client.read("005930", count = 2)

        assertEquals(LocalDate.of(2026, 9, 17), page.newest?.tradeDate)
        assertEquals(LocalDate.of(2026, 9, 16), page.oldest?.tradeDate)
        assertEquals(OffsetDateTime.parse("2026-09-15T00:00:00.000+09:00"), page.nextBefore)
        assertEquals(false, page.isLastPage)
    }

    @Test
    fun `nextBefore 가 null 이면 마지막 페이지다`() {
        val lastPage = """{"result":{"candles":[],"nextBefore":null}}"""
        server.expect(captureUri).andRespond(withSuccess(lastPage, MediaType.APPLICATION_JSON))

        val page = client.read("005930", count = 2)

        assertTrue(page.isLastPage)
        assertNull(page.nextBefore)
        assertTrue(page.candles.isEmpty())
    }

    @Test
    fun `before 의 타임존 오프셋 플러스는 %2B 로 인코딩된다`() {
        // 인코딩이 깨져도 예외가 나지 않고 엉뚱한 기간 데이터가 조용히 들어온다.
        // 최종 URI 문자열을 직접 확인하는 이 테스트가 유일한 방어선이다.
        server.expect(captureUri).andRespond(withSuccess(realPayload, MediaType.APPLICATION_JSON))

        client.read("005930", count = 2, before = OffsetDateTime.parse("2026-09-15T00:00:00+09:00"))

        val uri = requireNotNull(capturedUri)
        assertContains(uri, "%2B09%3A00", message = "before 의 +09:00 이 인코딩되지 않았다: $uri")
        assertTrue("+09:00" !in uri, "날것 + 가 쿼리스트링에 남아 있다: $uri")
    }

    @Test
    fun `before 가 없으면 쿼리에 아예 넣지 않는다`() {
        server.expect(captureUri).andRespond(withSuccess(realPayload, MediaType.APPLICATION_JSON))

        client.read("005930", count = 2)

        val uri = requireNotNull(capturedUri)
        assertTrue("before" !in uri, "before 를 빈 값으로라도 보내면 안 된다: $uri")
        assertContains(uri, "interval=1d")
        assertContains(uri, "adjusted=true")
        assertContains(uri, "symbol=005930")
    }

    @Test
    fun `count 상한을 넘기면 호출 전에 막는다`() {
        val ex = assertThrows<IllegalArgumentException> {
            client.read("005930", count = DailyCandleReader.MAX_COUNT + 1)
        }

        assertContains(ex.message!!, "200")
    }
}
