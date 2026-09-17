package dev.teolab.stocklab.schedule

import dev.teolab.stocklab.collection.application.JobRecorder
import dev.teolab.stocklab.collection.domain.CollectionLog
import dev.teolab.stocklab.collection.domain.CollectionLogRepository
import dev.teolab.stocklab.collection.domain.JobStatus
import dev.teolab.stocklab.market.application.DailyCandleCollector
import dev.teolab.stocklab.market.application.TradingTrendCollector
import dev.teolab.stocklab.market.domain.CandlePage
import dev.teolab.stocklab.market.domain.DailyCandle
import dev.teolab.stocklab.market.domain.TrendPage
import dev.teolab.stocklab.stock.application.StockMasterSync
import dev.teolab.stocklab.stock.domain.ListingStatus
import dev.teolab.stocklab.stock.domain.StockRepository
import dev.teolab.stocklab.stock.domain.Market
import dev.teolab.stocklab.stock.domain.Stock
import dev.teolab.stocklab.watchlist.application.CategoryWatchlistSync
import dev.teolab.stocklab.watchlist.application.Top30Refresher
import dev.teolab.stocklab.watchlist.domain.RankingSnapshot
import dev.teolab.stocklab.watchlist.domain.WatchEntry
import dev.teolab.stocklab.watchlist.domain.WatchSource
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyCollectionJobTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-17T09:00:00Z"), ZoneOffset.UTC)
    private val schedule = CollectionSchedule(dailyLookbackDays = 30)

    private class RecordingLogRepository : CollectionLogRepository {
        val finished = mutableListOf<Pair<String, JobStatus>>()
        private val names = mutableMapOf<Long, String>()
        private var nextId = 1L
        override fun start(jobName: String, symbol: String?, targetRange: String?, startedAt: Instant): Long {
            val id = nextId++
            names[id] = jobName
            return id
        }
        override fun finish(id: Long, status: JobStatus, message: String?, finishedAt: Instant) {
            finished += (names[id] ?: "?") to status
        }
        override fun findRecent(limit: Int): List<CollectionLog> = emptyList()
    }

    private class FakeWatchlist(private val symbols: List<String>) : WatchlistRepository {
        override fun upsertAll(entries: List<WatchEntry>): Int = entries.size
        override fun findDistinctSymbols(): List<String> = symbols
        override fun findBySource(source: WatchSource): List<WatchEntry> = emptyList()
        override fun deleteStale(source: WatchSource, threshold: Instant): Int = 0
        override fun deleteMissing(source: WatchSource, keep: Collection<Pair<String, String>>): Int = 0
    }

    private fun candle(symbol: String, date: LocalDate) = DailyCandle(
        symbol, date, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L, "KRW", true,
    )

    private fun job(
        symbols: List<String>,
        logRepository: CollectionLogRepository = RecordingLogRepository(),
        failingSymbols: Set<String> = emptySet(),
    ): DailyCollectionJob {
        val candleCollector = DailyCandleCollector(
            reader = object : dev.teolab.stocklab.market.domain.DailyCandleReader {
                override fun read(symbol: String, count: Int, before: java.time.OffsetDateTime?): CandlePage {
                    if (symbol in failingSymbols) error("$symbol 조회 실패")
                    return CandlePage(listOf(candle(symbol, LocalDate.of(2026, 9, 17))), null)
                }
            },
            store = { it.size },
        )
        val trendCollector = TradingTrendCollector(
            investorTradingReader = { _, _, _ -> TrendPage.empty() },
            investorTradingStore = { it.size },
            shortSellingReader = { _, _, _ -> TrendPage.empty() },
            shortSellingStore = { it.size },
        )
        return DailyCollectionJob(
            stockMasterSync = StockMasterSync(
                { market -> listOf(Stock("005930", "삼성전자", market, "KRW", ListingStatus.ACTIVE, "STOCK", null)) },
                object : StockRepository {
                    override fun upsertAll(stocks: List<Stock>) = stocks.size
                    override fun markDelisted(markets: Collection<Market>, activeSymbols: Collection<String>) = 0
                    override fun countByStatus(status: ListingStatus) = 0
                },
            ),
            categoryWatchlistSync = CategoryWatchlistSync({ emptyList() }, FakeWatchlist(symbols), clock),
            top30Refresher = Top30Refresher(
                rankingReader = { RankingSnapshot(OffsetDateTime.parse("2026-09-17T18:00:00+09:00"), emptyList()) },
                watchlistRepository = FakeWatchlist(symbols),
                historyStore = { 0 },
                clock = clock,
            ),
            candleCollector = candleCollector,
            trendCollector = trendCollector,
            watchlistRepository = FakeWatchlist(symbols),
            jobRecorder = JobRecorder(logRepository, clock),
            schedule = schedule,
            clock = clock,
        )
    }

    @Test
    fun `watchlist 전 종목의 일봉과 수급을 모은다`() {
        val result = job(listOf("005930", "000660")).collectDaily()

        assertEquals(2, result.symbols)
        assertEquals(2, result.candles)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `한 종목이 실패해도 나머지는 계속 간다`() {
        // 상장폐지 직후나 ETF 처럼 개별 사유로 실패하는 일은 흔하다. 전체가 멈추면 안 된다.
        val result = job(listOf("005930", "BROKEN", "000660"), failingSymbols = setOf("BROKEN")).collectDaily()

        assertEquals(listOf("BROKEN"), result.failures)
        assertEquals(2, result.candles, "나머지 두 종목은 수집돼야 한다")
    }

    @Test
    fun `절반 넘게 실패하면 배치를 실패로 본다`() {
        val logRepository = RecordingLogRepository()
        val failing = job(listOf("A", "B", "C"), logRepository, failingSymbols = setOf("A", "B"))

        assertThrows<IllegalStateException> { failing.collectDaily() }

        assertEquals(listOf(DailyCollectionJob.JOB_DAILY_COLLECT to JobStatus.FAILED), logRepository.finished)
    }

    @Test
    fun `성공하면 수집 이력에 SUCCESS 로 남는다`() {
        val logRepository = RecordingLogRepository()

        job(listOf("005930"), logRepository).collectDaily()

        assertEquals(listOf(DailyCollectionJob.JOB_DAILY_COLLECT to JobStatus.SUCCESS), logRepository.finished)
    }

    @Test
    fun `각 배치는 자기 이름으로 이력을 남긴다`() {
        val logRepository = RecordingLogRepository()
        val job = job(listOf("005930"), logRepository)

        job.syncStockMaster()
        job.refreshWatchlist()

        assertEquals(
            listOf(DailyCollectionJob.JOB_STOCK_MASTER, DailyCollectionJob.JOB_TOP30),
            logRepository.finished.map { it.first },
        )
    }
}
