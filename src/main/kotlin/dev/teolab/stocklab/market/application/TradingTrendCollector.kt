package dev.teolab.stocklab.market.application

import dev.teolab.stocklab.market.domain.CollectResult
import dev.teolab.stocklab.market.domain.DatedRecord
import dev.teolab.stocklab.market.domain.InvestorTradingReader
import dev.teolab.stocklab.market.domain.InvestorTradingStore
import dev.teolab.stocklab.market.domain.ShortSellingReader
import dev.teolab.stocklab.market.domain.ShortSellingStore
import dev.teolab.stocklab.market.domain.TrendPage
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * 수급(투자자별 매매동향 / 공매도)을 기간만큼 거슬러 올라가며 수집한다.
 *
 * 일봉과 페이징 규약이 달라 별도 수집기를 둔다. 커서가 `until`(날짜)이고 한 번에 최대 100건이다.
 * 저장은 역시 덮어쓰기다 — 당일 데이터는 장 마감 전이면 일부 주체가 null 로 오고,
 * 확정되면 값이 채워진 채 다시 내려오기 때문이다.
 */
class TradingTrendCollector(
    private val investorTradingReader: InvestorTradingReader,
    private val investorTradingStore: InvestorTradingStore,
    private val shortSellingReader: ShortSellingReader,
    private val shortSellingStore: ShortSellingStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun collectInvestorTrading(symbol: String, from: LocalDate, to: LocalDate): CollectResult =
        collect(
            symbol = symbol,
            from = from,
            to = to,
            label = "투자자별 매매동향",
            read = { until -> investorTradingReader.read(symbol, TrendPage.MAX_COUNT, until) },
            save = investorTradingStore::upsertAll,
        )

    fun collectShortSelling(symbol: String, from: LocalDate, to: LocalDate): CollectResult =
        collect(
            symbol = symbol,
            from = from,
            to = to,
            label = "공매도",
            read = { until -> shortSellingReader.read(symbol, TrendPage.MAX_COUNT, until) },
            save = shortSellingStore::upsertAll,
        )

    private fun <T : DatedRecord> collect(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
        label: String,
        read: (LocalDate?) -> TrendPage<T>,
        save: (List<T>) -> Int,
    ): CollectResult {
        require(!from.isAfter(to)) { "from($from) 이 to($to) 보다 뒤다" }

        // until 은 inclusive 라 to 당일 레코드도 포함된다.
        var until: LocalDate? = to
        var pages = 0
        var fetched = 0
        var saved = 0
        var oldest: LocalDate? = null
        var newest: LocalDate? = null

        while (pages < MAX_PAGES) {
            val page = read(until)
            pages++
            if (page.records.isEmpty()) break

            val inRange = page.records.filter { it.tradeDate in from..to }
            if (inRange.isNotEmpty()) {
                saved += save(inRange)
                fetched += inRange.size
                newest = newest ?: inRange.first().tradeDate
                oldest = inRange.last().tradeDate
            }

            val reachedStart = page.records.last().tradeDate <= from
            if (reachedStart || page.isLastPage) break
            until = page.nextUntil
        }

        if (pages >= MAX_PAGES) {
            log.warn("{} {} 페이지 상한({})에 걸렸다. 기간을 나눠 다시 돌릴 것", symbol, label, MAX_PAGES)
        }

        return CollectResult(symbol, from, to, fetched, saved, pages, oldest, newest)
            .also { log.info("{} {} 수집: {}건 저장 ({} ~ {}), {}페이지", symbol, label, it.saved, it.oldest, it.newest, it.pages) }
    }

    companion object {
        /** 페이지당 100건이므로 100페이지면 약 40년치다. nextUntil 이 전진하지 않는 경우의 방어선. */
        const val MAX_PAGES = 100
    }
}
