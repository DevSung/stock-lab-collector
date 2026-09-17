package dev.teolab.stocklab.market.application

import dev.teolab.stocklab.market.domain.InvestorTrading
import dev.teolab.stocklab.market.domain.ShortSelling
import dev.teolab.stocklab.market.domain.TrendPage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TradingTrendCollectorTest {

    private val symbol = "005930"

    private fun investor(date: LocalDate) = InvestorTrading(symbol, date, 100L, -200L, 300L, null)
    private fun short(date: LocalDate) =
        ShortSelling(symbol, date, 1_000L, BigDecimal("50000"), BigDecimal("0.05"), BigDecimal("0.06"))

    private fun investorPage(newest: LocalDate, size: Int, nextUntil: LocalDate?) =
        TrendPage((0 until size).map { investor(newest.minusDays(it.toLong())) }, nextUntil)

    private class Recorder<T> {
        val saved = mutableListOf<T>()
        var calls = 0
        fun save(records: List<T>): Int { calls++; saved += records; return records.size }
    }

    private fun collector(
        pages: List<TrendPage<InvestorTrading>>,
        recorder: Recorder<InvestorTrading>,
        untilLog: MutableList<LocalDate?> = mutableListOf(),
    ): TradingTrendCollector {
        var index = 0
        return TradingTrendCollector(
            investorTradingReader = { _, _, until ->
                untilLog += until
                pages.getOrElse(index++) { TrendPage.empty() }
            },
            investorTradingStore = recorder::save,
            shortSellingReader = { _, _, _ -> TrendPage.empty() },
            shortSellingStore = { 0 },
        )
    }

    @Test
    fun `nextUntil 을 따라가며 여러 페이지를 모은다`() {
        val recorder = Recorder<InvestorTrading>()
        val untilLog = mutableListOf<LocalDate?>()
        val pages = listOf(
            investorPage(LocalDate.of(2026, 9, 17), 3, nextUntil = LocalDate.of(2026, 9, 14)),
            investorPage(LocalDate.of(2026, 9, 14), 3, nextUntil = null),
        )

        val result = collector(pages, recorder, untilLog)
            .collectInvestorTrading(symbol, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 17))

        assertEquals(2, result.pages)
        assertEquals(6, result.saved)
        // 첫 요청은 to 당일(inclusive), 다음은 응답이 준 nextUntil 그대로
        assertEquals(listOf(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 14)), untilLog)
    }

    @Test
    fun `기간 밖 레코드는 저장하지 않는다`() {
        val recorder = Recorder<InvestorTrading>()
        val pages = listOf(investorPage(LocalDate.of(2026, 9, 17), 5, nextUntil = null))

        collector(pages, recorder)
            .collectInvestorTrading(symbol, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16))

        assertEquals(
            listOf(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 15)),
            recorder.saved.map { it.tradeDate },
        )
    }

    @Test
    fun `빈 응답이면 저장을 시도하지 않는다`() {
        val recorder = Recorder<InvestorTrading>()

        val result = collector(listOf(TrendPage.empty()), recorder)
            .collectInvestorTrading(symbol, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 17))

        assertTrue(result.isEmpty)
        assertEquals(0, recorder.calls)
    }

    @Test
    fun `nextUntil 이 전진하지 않아도 페이지 상한에서 멈춘다`() {
        val recorder = Recorder<InvestorTrading>()
        val stuck = TradingTrendCollector(
            investorTradingReader = { _, _, _ ->
                investorPage(LocalDate.of(2026, 9, 17), 3, nextUntil = LocalDate.of(2026, 9, 17))
            },
            investorTradingStore = recorder::save,
            shortSellingReader = { _, _, _ -> TrendPage.empty() },
            shortSellingStore = { 0 },
        )

        val result = stuck.collectInvestorTrading(symbol, LocalDate.of(1990, 1, 1), LocalDate.of(2026, 9, 17))

        assertEquals(TradingTrendCollector.MAX_PAGES, result.pages)
    }

    @Test
    fun `공매도도 같은 방식으로 모은다`() {
        val recorder = Recorder<ShortSelling>()
        val collector = TradingTrendCollector(
            investorTradingReader = { _, _, _ -> TrendPage.empty() },
            investorTradingStore = { 0 },
            shortSellingReader = { _, _, _ ->
                // 공매도는 공시가 하루 늦어 당일(9/17)이 없는 것이 정상이다
                TrendPage(listOf(short(LocalDate.of(2026, 9, 16)), short(LocalDate.of(2026, 9, 15))), null)
            },
            shortSellingStore = recorder::save,
        )

        val result = collector.collectShortSelling(symbol, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 17))

        assertEquals(2, result.saved)
        assertEquals(LocalDate.of(2026, 9, 16), result.newest)
    }

    @Test
    fun `from 이 to 보다 뒤면 거부한다`() {
        assertThrows<IllegalArgumentException> {
            collector(emptyList(), Recorder())
                .collectInvestorTrading(symbol, LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 1))
        }
    }
}
