package dev.teolab.stocklab.collection.infrastructure.config

import dev.teolab.stocklab.collection.application.JobRecorder
import dev.teolab.stocklab.collection.domain.CollectionLogRepository
import dev.teolab.stocklab.collection.infrastructure.persistence.CollectionLogJdbcRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class CollectionConfig {

    @Bean
    fun collectionLogRepository(jdbcTemplate: JdbcTemplate): CollectionLogRepository =
        CollectionLogJdbcRepository(jdbcTemplate)

    @Bean
    fun jobRecorder(repository: CollectionLogRepository, clock: Clock): JobRecorder =
        JobRecorder(repository, clock)
}
