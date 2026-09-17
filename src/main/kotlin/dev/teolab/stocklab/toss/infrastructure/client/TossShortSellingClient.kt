package dev.teolab.stocklab.toss.infrastructure.client

import dev.teolab.stocklab.market.domain.ShortSelling
import dev.teolab.stocklab.market.domain.ShortSellingReader
import dev.teolab.stocklab.market.domain.TrendPage
import dev.teolab.stocklab.toss.infrastructure.client.dto.TossShortSellingEnvelope
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import org.springframework.web.client.RestClient
import java.time.LocalDate

/** 공매도 동향 조회. 공시가 하루 늦어서 최근 하루가 비어 있는 것이 정상이다. */
class TossShortSellingClient(
    private val tossApiRestClient: RestClient,
    private val executor: TossApiExecutor,
) : ShortSellingReader {

    override fun read(symbol: String, count: Int, until: LocalDate?): TrendPage<ShortSelling> {
        val result = TossTrendRequest.fetch(
            restClient = tossApiRestClient,
            executor = executor,
            pathTemplate = PATH,
            responseType = TossShortSellingEnvelope::class.java,
            symbol = symbol,
            count = count,
            until = until,
        )?.result ?: return TrendPage.empty()

        return TrendPage(
            records = result.records.map { record ->
                ShortSelling(
                    symbol = symbol,
                    tradeDate = LocalDate.parse(record.date),
                    volume = record.shortSellingVolume,
                    amount = record.shortSellingAmount,
                    volumeRate = record.shortSellingVolumeRate,
                    amountRate = record.shortSellingAmountRate,
                )
            },
            nextUntil = result.nextUntil?.let(LocalDate::parse),
        )
    }

    companion object {
        const val PATH = "/api/v1/stocks/{symbol}/short-selling"
    }
}
