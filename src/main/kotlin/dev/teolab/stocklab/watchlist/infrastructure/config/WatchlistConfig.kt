package dev.teolab.stocklab.watchlist.infrastructure.config

import dev.teolab.stocklab.toss.infrastructure.client.TossRankingClient
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import dev.teolab.stocklab.watchlist.application.CategoryWatchlistSync
import dev.teolab.stocklab.watchlist.application.Top30Refresher
import dev.teolab.stocklab.watchlist.domain.RankingReader
import dev.teolab.stocklab.watchlist.domain.Top30HistoryStore
import dev.teolab.stocklab.watchlist.domain.WatchCategoryLoader
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import dev.teolab.stocklab.watchlist.infrastructure.persistence.Top30HistoryJdbcStore
import dev.teolab.stocklab.watchlist.infrastructure.persistence.WatchlistJdbcRepository
import org.springframework.context.annotation.Bean
import dev.teolab.stocklab.config.Profiles
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@Profile(Profiles.REQUIRES_DATABASE)
class WatchlistConfig {

    @Bean
    fun watchCategoryLoader(): WatchCategoryLoader = YamlWatchCategoryLoader()

    @Bean
    fun watchlistRepository(jdbcTemplate: JdbcTemplate): WatchlistRepository =
        WatchlistJdbcRepository(jdbcTemplate)

    @Bean
    fun top30HistoryStore(jdbcTemplate: JdbcTemplate, clock: Clock): Top30HistoryStore =
        Top30HistoryJdbcStore(jdbcTemplate, clock)

    @Bean
    fun rankingReader(
        tossApiRestClient: RestClient,
        tossApiExecutor: TossApiExecutor,
        clock: Clock,
    ): RankingReader = TossRankingClient(tossApiRestClient, tossApiExecutor, clock)

    @Bean
    fun categoryWatchlistSync(
        loader: WatchCategoryLoader,
        repository: WatchlistRepository,
        clock: Clock,
    ): CategoryWatchlistSync = CategoryWatchlistSync(loader, repository, clock)

    @Bean
    fun top30Refresher(
        rankingReader: RankingReader,
        watchlistRepository: WatchlistRepository,
        top30HistoryStore: Top30HistoryStore,
        clock: Clock,
    ): Top30Refresher = Top30Refresher(rankingReader, watchlistRepository, top30HistoryStore, clock)
}
