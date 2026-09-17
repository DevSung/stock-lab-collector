package dev.teolab.stocklab.toss.infrastructure.client

import dev.teolab.stocklab.market.domain.InvestorTrading
import dev.teolab.stocklab.market.domain.InvestorTradingReader
import dev.teolab.stocklab.market.domain.TrendPage
import dev.teolab.stocklab.toss.infrastructure.client.dto.TossInvestorTradingEnvelope
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import org.springframework.web.client.RestClient
import java.time.LocalDate

/** 투자자별 매매동향 조회. 국내 종목만 지원되며, 해외 종목은 unsupported-market 으로 떨어진다. */
class TossInvestorTradingClient(
    private val tossApiRestClient: RestClient,
    private val executor: TossApiExecutor,
) : InvestorTradingReader {

    override fun read(symbol: String, count: Int, until: LocalDate?): TrendPage<InvestorTrading> {
        val result = TossTrendRequest.fetch(
            restClient = tossApiRestClient,
            executor = executor,
            pathTemplate = PATH,
            responseType = TossInvestorTradingEnvelope::class.java,
            symbol = symbol,
            count = count,
            until = until,
        )?.result ?: return TrendPage.empty()

        return TrendPage(
            records = result.records.map { record ->
                InvestorTrading(
                    symbol = symbol,
                    tradeDate = LocalDate.parse(record.date),
                    individualNet = record.individual?.netBuyVolume,
                    foreignerNet = record.foreigner?.netBuyVolume,
                    institutionNet = record.institution?.netBuyVolume,
                    otherCorporationNet = record.otherCorporation?.netBuyVolume,
                )
            },
            nextUntil = result.nextUntil?.let(LocalDate::parse),
        )
    }

    companion object {
        const val PATH = "/api/v1/stocks/{symbol}/investor-trading"
    }
}
