package dev.teolab.stocklab.toss.infrastructure.client

import dev.teolab.stocklab.market.domain.TrendPage
import dev.teolab.stocklab.toss.infrastructure.ratelimit.ApiGroup
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import org.springframework.web.client.RestClient
import java.time.LocalDate

/**
 * 수급 API 두 개가 공유하는 요청 규약.
 *
 * 캔들과 다르다.
 *   캔들  before=ISO 일시, count 최대 200
 *   수급  until=날짜(inclusive), count 최대 100   (API 가 constraint 로 알려준 값)
 *
 * 두 API 모두 STOCK_TRADING_TREND 그룹이라 초당 한도를 함께 쓴다.
 */
internal object TossTrendRequest {

    fun <T : Any> fetch(
        restClient: RestClient,
        executor: TossApiExecutor,
        pathTemplate: String,
        responseType: Class<T>,
        symbol: String,
        count: Int,
        until: LocalDate?,
    ): T? {
        require(count in 1..TrendPage.MAX_COUNT) { "count 는 1..${TrendPage.MAX_COUNT} 여야 한다: $count" }
        return executor.execute(ApiGroup.STOCK_TRADING_TREND) {
            restClient.get()
                .uri { builder ->
                    builder.path(pathTemplate)
                        .queryParam("count", "{count}")
                        .apply { if (until != null) queryParam("until", "{until}") }
                        .build(
                            buildMap<String, Any> {
                                put("symbol", symbol)
                                put("count", count)
                                until?.let { put("until", it.toString()) }
                            },
                        )
                }
                .retrieve()
                .body(responseType)
        }
    }
}
