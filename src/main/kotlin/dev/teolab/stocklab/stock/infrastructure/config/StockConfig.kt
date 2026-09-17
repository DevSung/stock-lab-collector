package dev.teolab.stocklab.stock.infrastructure.config

import dev.teolab.stocklab.stock.application.StockMasterSync
import dev.teolab.stocklab.stock.domain.StockMasterReader
import dev.teolab.stocklab.stock.domain.StockRepository
import dev.teolab.stocklab.stock.infrastructure.persistence.StockJdbcRepository
import dev.teolab.stocklab.toss.infrastructure.client.TossStockMasterClient
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import org.springframework.context.annotation.Bean
import dev.teolab.stocklab.config.Profiles
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@Profile(Profiles.REQUIRES_DATABASE)
class StockConfig {

    @Bean
    fun stockMasterReader(tossApiRestClient: RestClient, tossApiExecutor: TossApiExecutor): StockMasterReader =
        TossStockMasterClient(tossApiRestClient, tossApiExecutor)

    @Bean
    fun stockRepository(jdbcTemplate: JdbcTemplate, clock: Clock): StockRepository =
        StockJdbcRepository(jdbcTemplate, clock)

    @Bean
    fun stockMasterSync(reader: StockMasterReader, repository: StockRepository): StockMasterSync =
        StockMasterSync(reader, repository)
}
