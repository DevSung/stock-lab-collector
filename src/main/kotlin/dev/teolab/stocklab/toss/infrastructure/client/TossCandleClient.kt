package dev.teolab.stocklab.toss.infrastructure.client

import dev.teolab.stocklab.market.domain.CandlePage
import dev.teolab.stocklab.market.domain.DailyCandle
import dev.teolab.stocklab.market.domain.DailyCandleReader
import dev.teolab.stocklab.toss.infrastructure.client.dto.TossCandleEnvelope
import dev.teolab.stocklab.toss.infrastructure.client.dto.TossCandleItem
import dev.teolab.stocklab.toss.infrastructure.ratelimit.ApiGroup
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * [DailyCandleReader] 구현.
 *
 * before 의 타임존 오프셋 `+09:00` 은 쿼리스트링에서 `%2B` 로 인코딩되어야 한다.
 * `tossApiRestClient` 의 uriBuilderFactory 가 VALUES_ONLY 모드라, **URI 변수로 넘긴 값만** 엄격하게 인코딩된다.
 * 그래서 아래처럼 `{before}` 자리표시자를 쓰고 값을 따로 넘긴다. 문자열을 직접 이어 붙이면 인코딩되지 않는다.
 *
 * (실측 결과 토스 서버는 깨진 `+`(공백)도 관대하게 받아준다. 그래도 규격대로 보낸다 —
 *  이런 관대함은 예고 없이 사라지고, 그때 증상은 예외가 아니라 조용히 어긋난 데이터다.)
 */
class TossCandleClient(
    private val tossApiRestClient: RestClient,
    private val executor: TossApiExecutor,
) : DailyCandleReader {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun read(symbol: String, count: Int, before: OffsetDateTime?): CandlePage {
        require(count in 1..DailyCandleReader.MAX_COUNT) {
            "count 는 1..${DailyCandleReader.MAX_COUNT} 여야 한다: $count"
        }

        val envelope = executor.execute(ApiGroup.MARKET_DATA_CHART) {
            tossApiRestClient.get()
                .uri { builder ->
                    builder.path(CANDLES_PATH)
                        .queryParam("symbol", "{symbol}")
                        .queryParam("interval", "{interval}")
                        .queryParam("count", "{count}")
                        .queryParam("adjusted", "{adjusted}")
                        .apply { if (before != null) queryParam("before", "{before}") }
                        .build(buildVariables(symbol, count, before))
                }
                .retrieve()
                .body(TossCandleEnvelope::class.java)
        }

        val result = envelope?.result
        if (result == null) {
            log.warn("{} 일봉 응답에 result 가 없다", symbol)
            return CandlePage.EMPTY
        }

        val candles = result.candles.map { it.toDomain(symbol) }
        log.debug(
            "{} 일봉 {}건 수신 ({} ~ {}), nextBefore={}",
            symbol,
            candles.size,
            candles.lastOrNull()?.tradeDate,
            candles.firstOrNull()?.tradeDate,
            result.nextBefore,
        )
        return CandlePage(candles, result.nextBefore?.let(OffsetDateTime::parse))
    }

    private fun buildVariables(symbol: String, count: Int, before: OffsetDateTime?): Map<String, Any> =
        buildMap {
            put("symbol", symbol)
            put("interval", DAILY_INTERVAL)
            put("count", count)
            put("adjusted", true)
            // ISO_OFFSET_DATE_TIME 이 "+09:00" 형태로 내보내고, VALUES_ONLY 인코딩이 이를 %2B 로 바꾼다.
            before?.let { put("before", ISO_OFFSET.format(it)) }
        }

    private fun TossCandleItem.toDomain(symbol: String): DailyCandle {
        val at = OffsetDateTime.parse(timestamp)
        return DailyCandle(
            symbol = symbol,
            // 봉의 타임스탬프는 KST 자정이다. 거래일은 KST 기준으로 뽑아야 한다.
            tradeDate = at.atZoneSameInstant(KST).toLocalDate(),
            open = openPrice,
            high = highPrice,
            low = lowPrice,
            close = closePrice,
            volume = volume,
            currency = currency,
            adjusted = true,
        )
    }

    companion object {
        const val CANDLES_PATH = "/api/v1/candles"
        const val DAILY_INTERVAL = "1d"
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private val ISO_OFFSET: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}
