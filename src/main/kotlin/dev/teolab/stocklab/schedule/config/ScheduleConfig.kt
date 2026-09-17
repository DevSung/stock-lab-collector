package dev.teolab.stocklab.schedule.config

import dev.teolab.stocklab.collection.application.JobRecorder
import dev.teolab.stocklab.market.application.DailyCandleCollector
import dev.teolab.stocklab.market.application.TradingTrendCollector
import dev.teolab.stocklab.schedule.CollectionSchedule
import dev.teolab.stocklab.schedule.DailyCollectionJob
import dev.teolab.stocklab.stock.application.StockMasterSync
import dev.teolab.stocklab.watchlist.application.CategoryWatchlistSync
import dev.teolab.stocklab.watchlist.application.Top30Refresher
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ScheduleConfig {

    @Bean
    fun dailyCollectionJob(
        stockMasterSync: StockMasterSync,
        categoryWatchlistSync: CategoryWatchlistSync,
        top30Refresher: Top30Refresher,
        candleCollector: DailyCandleCollector,
        trendCollector: TradingTrendCollector,
        watchlistRepository: WatchlistRepository,
        jobRecorder: JobRecorder,
        schedule: CollectionSchedule,
        clock: Clock,
    ): DailyCollectionJob = DailyCollectionJob(
        stockMasterSync, categoryWatchlistSync, top30Refresher,
        candleCollector, trendCollector, watchlistRepository, jobRecorder, schedule, clock,
    )
}
