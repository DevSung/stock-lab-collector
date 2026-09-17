package dev.teolab.stocklab.schedule

import dev.teolab.stocklab.collection.application.JobRecorder
import dev.teolab.stocklab.market.application.DailyCandleCollector
import dev.teolab.stocklab.market.application.TradingTrendCollector
import dev.teolab.stocklab.stock.application.StockMasterSync
import dev.teolab.stocklab.watchlist.application.CategoryWatchlistSync
import dev.teolab.stocklab.watchlist.application.Top30Refresher
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 배치 본체. 스케줄 트리거는 [CollectionScheduler] 가 걸고, 여기는 순수한 실행 로직이다.
 * 이렇게 나눠두면 CLI 로도 같은 작업을 돌릴 수 있고 테스트도 쉽다.
 */
class DailyCollectionJob(
    private val stockMasterSync: StockMasterSync,
    private val categoryWatchlistSync: CategoryWatchlistSync,
    private val top30Refresher: Top30Refresher,
    private val candleCollector: DailyCandleCollector,
    private val trendCollector: TradingTrendCollector,
    private val watchlistRepository: WatchlistRepository,
    private val jobRecorder: JobRecorder,
    private val schedule: CollectionSchedule,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val zone: ZoneId = ZoneId.of(schedule.zone)

    fun syncStockMaster(): StockMasterSync.SyncResult =
        jobRecorder.record(JOB_STOCK_MASTER) { stockMasterSync.sync() }

    fun refreshWatchlist(): Top30Refresher.RefreshResult = jobRecorder.record(JOB_TOP30) {
        categoryWatchlistSync.sync()
        top30Refresher.refresh()
    }

    /**
     * watchlist 종목의 일봉과 수급을 받는다.
     *
     * **한 종목이 실패해도 나머지는 계속 간다.** 상장폐지 직후거나 ETF 라 수급이 없는 경우처럼
     * 개별 종목 사유로 실패하는 일이 흔한데, 그것 때문에 전체가 멈추면 안 된다.
     * 다만 절반 넘게 실패하면 뭔가 근본적으로 잘못된 것이므로 배치를 실패로 처리한다.
     */
    fun collectDaily(): DailyResult {
        val today = LocalDate.ofInstant(clock.instant(), zone)
        val from = today.minusDays(schedule.dailyLookbackDays)
        val symbols = watchlistRepository.findDistinctSymbols()
        val range = "$from~$today"

        return jobRecorder.record(JOB_DAILY_COLLECT, targetRange = range) {
            var candles = 0
            var investor = 0
            var shorts = 0
            val failures = mutableListOf<String>()

            symbols.forEach { symbol ->
                runCatching {
                    candles += candleCollector.collect(symbol, from, today).saved
                    investor += trendCollector.collectInvestorTrading(symbol, from, today).saved
                    shorts += trendCollector.collectShortSelling(symbol, from, today).saved
                }.onFailure { e ->
                    failures += symbol
                    log.warn("{} 수집 실패 — 건너뛴다: {}", symbol, e.message)
                }
            }

            val result = DailyResult(symbols.size, candles, investor, shorts, failures)
            check(!result.exceedsFailureThreshold(schedule.failureThreshold)) {
                "종목 ${symbols.size}개 중 ${failures.size}개가 실패했다: ${failures.take(10)}"
            }
            log.info("일일 수집 완료: {}", result)
            result
        }
    }

    data class DailyResult(
        val symbols: Int,
        val candles: Int,
        val investorTrading: Int,
        val shortSelling: Int,
        val failures: List<String>,
    ) {
        fun exceedsFailureThreshold(threshold: Double): Boolean =
            symbols > 0 && failures.size.toDouble() / symbols > threshold

        override fun toString(): String =
            "종목 $symbols · 일봉 $candles · 투자자 $investorTrading · 공매도 $shortSelling · 실패 ${failures.size}"
    }

    companion object {
        const val JOB_STOCK_MASTER = "stock-master-sync"
        const val JOB_TOP30 = "watchlist-refresh"
        const val JOB_DAILY_COLLECT = "daily-collect"
    }
}
