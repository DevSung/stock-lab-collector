package dev.teolab.stocklab.watchlist.application

import dev.teolab.stocklab.watchlist.domain.RankingReader
import dev.teolab.stocklab.watchlist.domain.Top30HistoryStore
import dev.teolab.stocklab.watchlist.domain.WatchEntry
import dev.teolab.stocklab.watchlist.domain.WatchSource
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/**
 * 거래대금 TOP30 을 갱신한다.
 *
 * 이탈 종목을 **즉시 지우지 않는다.** 바로 지우면 다시 들어올 때까지의 기간이 데이터 구멍이 된다.
 * [gracePeriod] 동안은 계속 수집하고, 그 안에 재진입하면 last_seen_at 이 갱신되어 살아남는다.
 *
 * 순위 자체는 [Top30HistoryStore] 에 날짜별로 남긴다. watchlist 에는 "지금 수집할 종목"만 담기므로
 * 과거에 누가 상위권이었는지는 그쪽을 봐야 한다.
 */
class Top30Refresher(
    private val rankingReader: RankingReader,
    private val watchlistRepository: WatchlistRepository,
    private val historyStore: Top30HistoryStore,
    private val clock: Clock,
    private val gracePeriod: Duration = DEFAULT_GRACE_PERIOD,
    private val topCount: Int = DEFAULT_TOP_COUNT,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun refresh(): RefreshResult {
        val now = clock.instant()
        val snapshot = rankingReader.readTopByTradingAmount(topCount)
        if (snapshot.isEmpty) {
            log.warn("랭킹이 비어 있다. watchlist 를 건드리지 않고 끝낸다")
            return RefreshResult(0, 0, 0)
        }

        val historyRows = historyStore.save(snapshot)

        val entries = snapshot.symbols.map { symbol ->
            WatchEntry(symbol = symbol, source = WatchSource.TOP30, addedAt = now, lastSeenAt = now)
        }
        val upserted = watchlistRepository.upsertAll(entries)

        // 유예 기간이 지나도록 한 번도 다시 들지 못한 종목만 정리한다.
        val removed = watchlistRepository.deleteStale(WatchSource.TOP30, now.minus(gracePeriod))

        return RefreshResult(upserted, removed, historyRows).also {
            log.info(
                "TOP30 갱신: {}종목 반영, 유예({}일) 만료 {}건 제거, 이력 {}행 (집계 {})",
                it.refreshed, gracePeriod.toDays(), it.removed, it.historyRows, snapshot.rankedAt,
            )
        }
    }

    data class RefreshResult(
        val refreshed: Int,
        val removed: Int,
        val historyRows: Int,
    )

    companion object {
        val DEFAULT_GRACE_PERIOD: Duration = Duration.ofDays(30)
        const val DEFAULT_TOP_COUNT = 30
    }
}
