package dev.teolab.stocklab.watchlist.application

import dev.teolab.stocklab.watchlist.domain.WatchCategoryLoader
import dev.teolab.stocklab.watchlist.domain.WatchEntry
import dev.teolab.stocklab.watchlist.domain.WatchSource
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * yml 카테고리를 watchlist 테이블에 반영한다.
 *
 * **yml 이 진실이다.** 파일에서 빠진 (종목, 카테고리) 조합은 DB 에서도 지운다.
 * 단 지우는 범위는 CATEGORY 출처 행뿐이라, 같은 종목이 TOP30 이나 MANUAL 로도 들어와 있으면 그건 남는다.
 */
class CategoryWatchlistSync(
    private val loader: WatchCategoryLoader,
    private val repository: WatchlistRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sync(): SyncResult {
        val now = clock.instant()
        val categories = loader.loadAll()

        val entries = categories.flatMap { category ->
            category.symbols.map { symbol ->
                WatchEntry(
                    symbol = symbol,
                    source = WatchSource.CATEGORY,
                    category = category.category,
                    addedAt = now,
                    lastSeenAt = now,
                )
            }
        }

        val upserted = repository.upsertAll(entries)
        val keep = entries.map { it.symbol to it.category }
        val removed = repository.deleteMissing(WatchSource.CATEGORY, keep)

        return SyncResult(categories.size, entries.size, upserted, removed)
            .also { log.info("카테고리 동기화: {}개 카테고리 / {}종목 반영, {}건 제거", it.categories, it.symbols, it.removed) }
    }

    data class SyncResult(
        val categories: Int,
        val symbols: Int,
        val upserted: Int,
        val removed: Int,
    )
}
