package dev.teolab.stocklab.toss.infrastructure.client

import dev.teolab.stocklab.toss.infrastructure.client.dto.TossRankingEnvelope
import dev.teolab.stocklab.toss.infrastructure.ratelimit.ApiGroup
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import dev.teolab.stocklab.watchlist.domain.RankingEntry
import dev.teolab.stocklab.watchlist.domain.RankingReader
import dev.teolab.stocklab.watchlist.domain.RankingSnapshot
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.OffsetDateTime

/**
 * [RankingReader] 구현.
 *
 * 파라미터 값은 전부 API 가 `allowedValues` 로 알려준 것을 그대로 쓴다(추측 아님).
 *   type          MARKET_TRADING_AMOUNT | MARKET_TRADING_VOLUME | TOP_GAINERS | TOP_LOSERS
 *                 | TOSS_SECURITIES_TRADING_AMOUNT | TOSS_SECURITIES_TRADING_VOLUME
 *   marketCountry KR | US
 *   duration      realtime | 1d | 1w | 1mo | 3mo | 6mo | 1y
 */
class TossRankingClient(
    private val tossApiRestClient: RestClient,
    private val executor: TossApiExecutor,
    private val clock: Clock,
) : RankingReader {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun readTopByTradingAmount(count: Int): RankingSnapshot {
        require(count >= 1) { "count 는 1 이상이어야 한다: $count" }

        val envelope = executor.execute(ApiGroup.STOCK) {
            tossApiRestClient.get()
                .uri { builder ->
                    builder.path(RANKINGS_PATH)
                        .queryParam("type", "{type}")
                        .queryParam("marketCountry", "{marketCountry}")
                        .queryParam("duration", "{duration}")
                        .queryParam("count", "{count}")
                        .build(
                            mapOf(
                                "type" to TRADING_AMOUNT_TYPE,
                                "marketCountry" to KOREA,
                                "duration" to DAILY_DURATION,
                                "count" to count,
                            ),
                        )
                }
                .retrieve()
                .body(TossRankingEnvelope::class.java)
        }

        val result = envelope?.result
        if (result == null || result.rankings.isEmpty()) {
            log.warn("랭킹 응답이 비어 있다")
            return RankingSnapshot(OffsetDateTime.now(clock), emptyList())
        }

        val entries = result.rankings.map { item ->
            RankingEntry(
                rank = item.rank,
                symbol = item.symbol,
                tradingAmount = item.tradingAmount,
                tradingVolume = item.tradingVolume,
                lastPrice = item.price?.lastPrice,
                changeRate = item.price?.changeRate,
            )
        }
        // rankedAt 이 없으면 현재 시각으로 대체한다. 이력 테이블의 거래일을 정하는 값이라 비울 수 없다.
        val rankedAt = result.rankedAt?.let(OffsetDateTime::parse) ?: OffsetDateTime.now(clock)
        return RankingSnapshot(rankedAt, entries)
    }

    companion object {
        const val RANKINGS_PATH = "/api/v1/rankings"
        const val TRADING_AMOUNT_TYPE = "MARKET_TRADING_AMOUNT"
        const val KOREA = "KR"
        const val DAILY_DURATION = "1d"
    }
}
