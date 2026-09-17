package dev.teolab.stocklab.toss.infrastructure.client

import dev.teolab.stocklab.stock.domain.ListingStatus
import dev.teolab.stocklab.stock.domain.Market
import dev.teolab.stocklab.stock.domain.Stock
import dev.teolab.stocklab.stock.domain.StockMasterReader
import dev.teolab.stocklab.toss.infrastructure.client.dto.TossStockAllEnvelope
import dev.teolab.stocklab.toss.infrastructure.ratelimit.ApiGroup
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient

/**
 * 마켓별 전체 종목 조회.
 *
 * STOCK_ALL 그룹은 초당 1회로 가장 빡빡하다(실측: 연속 호출하면 바로 429).
 * 주 1회 배치라 문제는 없지만, 그룹별 rate limiter 가 실제로 필요한 이유를 보여주는 엔드포인트다.
 */
class TossStockMasterClient(
    private val tossApiRestClient: RestClient,
    private val executor: TossApiExecutor,
) : StockMasterReader {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun readAll(market: Market): List<Stock> {
        val envelope = executor.execute(ApiGroup.STOCK_ALL) {
            tossApiRestClient.get()
                .uri { builder ->
                    builder.path(PATH)
                        .queryParam("market", "{market}")
                        .build(mapOf("market" to market.name))
                }
                .retrieve()
                .body(TossStockAllEnvelope::class.java)
        }

        val items = envelope?.result.orEmpty()
        log.info("{} 전체 종목 {}건 수신", market, items.size)

        return items.map { item ->
            Stock(
                symbol = item.symbol,
                name = item.name,
                market = market,
                currency = market.currency,
                // 목록에 있다는 것 자체가 상장 상태라는 뜻이다.
                listingStatus = ListingStatus.ACTIVE,
                securityType = item.securityType,
                isinCode = item.isinCode,
            )
        }
    }

    companion object {
        const val PATH = "/api/v1/stocks/all"
    }
}
