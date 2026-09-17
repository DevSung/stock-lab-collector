package dev.teolab.stocklab.bootstrap

import dev.teolab.stocklab.watchlist.application.CategoryWatchlistSync
import dev.teolab.stocklab.watchlist.application.Top30Refresher
import dev.teolab.stocklab.watchlist.domain.WatchSource
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * watchlist 동기화 확인용(4단계). 스케줄러는 6단계에서 붙인다.
 *
 *   ./scripts/run.sh --spring.profiles.active=watchlist            # yml 동기화 + TOP30 갱신
 *   ./scripts/run.sh --spring.profiles.active=watchlist --skip-top30  # yml 만
 */
@Component
@Profile("watchlist")
class WatchlistSyncRunner(
    private val categorySync: CategoryWatchlistSync,
    private val top30Refresher: Top30Refresher,
    private val repository: WatchlistRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val categoryResult = categorySync.sync()
        println("카테고리: ${categoryResult.categories}개 / ${categoryResult.symbols}종목 반영, ${categoryResult.removed}건 제거")

        if (!args.containsOption("skip-top30")) {
            val top30Result = top30Refresher.refresh()
            println("TOP30: ${top30Result.refreshed}종목 반영, ${top30Result.removed}건 정리, 이력 ${top30Result.historyRows}행")
        }

        val symbols = repository.findDistinctSymbols()
        println()
        println("수집 대상 ${symbols.size}종목")
        WatchSource.entries.forEach { source ->
            val entries = repository.findBySource(source)
            if (entries.isEmpty()) return@forEach
            val detail = entries.joinToString(", ") {
                if (it.category.isBlank()) it.symbol else "${it.symbol}(${it.category})"
            }
            println("  ${source.name.padEnd(8)} ${entries.size}건  $detail")
        }
        println()
    }
}
