package dev.teolab.stocklab.market.infrastructure.config

import dev.teolab.stocklab.market.application.DailyCandleCollector
import dev.teolab.stocklab.market.application.TradingTrendCollector
import dev.teolab.stocklab.market.domain.DailyCandleReader
import dev.teolab.stocklab.market.domain.DailyCandleStore
import dev.teolab.stocklab.market.domain.InvestorTradingReader
import dev.teolab.stocklab.market.domain.InvestorTradingStore
import dev.teolab.stocklab.market.domain.ShortSellingReader
import dev.teolab.stocklab.market.domain.ShortSellingStore
import dev.teolab.stocklab.market.infrastructure.persistence.DailyCandleJdbcStore
import dev.teolab.stocklab.market.infrastructure.persistence.InvestorTradingJdbcStore
import dev.teolab.stocklab.market.infrastructure.persistence.ShortSellingJdbcStore
import org.springframework.context.annotation.Bean
import dev.teolab.stocklab.config.Profiles
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@Profile(Profiles.REQUIRES_DATABASE)
class MarketConfig {

    @Bean
    fun dailyCandleStore(jdbcTemplate: JdbcTemplate, clock: Clock): DailyCandleStore =
        DailyCandleJdbcStore(jdbcTemplate, clock)

    @Bean
    fun dailyCandleCollector(reader: DailyCandleReader, store: DailyCandleStore): DailyCandleCollector =
        DailyCandleCollector(reader, store)

    @Bean
    fun investorTradingStore(jdbcTemplate: JdbcTemplate, clock: Clock): InvestorTradingStore =
        InvestorTradingJdbcStore(jdbcTemplate, clock)

    @Bean
    fun shortSellingStore(jdbcTemplate: JdbcTemplate, clock: Clock): ShortSellingStore =
        ShortSellingJdbcStore(jdbcTemplate, clock)

    @Bean
    fun tradingTrendCollector(
        investorTradingReader: InvestorTradingReader,
        investorTradingStore: InvestorTradingStore,
        shortSellingReader: ShortSellingReader,
        shortSellingStore: ShortSellingStore,
    ): TradingTrendCollector = TradingTrendCollector(
        investorTradingReader, investorTradingStore, shortSellingReader, shortSellingStore,
    )
}
