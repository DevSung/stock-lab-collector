package dev.teolab.stocklab.watchlist.infrastructure.persistence

import dev.teolab.stocklab.watchlist.domain.WatchEntry
import dev.teolab.stocklab.watchlist.domain.WatchSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.transaction.annotation.Transactional
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 실제 MySQL 통합 테스트. `docker compose up -d` 가 떠 있어야 한다. */
@SpringBootTest(
    classes = [
        DataSourceAutoConfiguration::class,
        DataSourceTransactionManagerAutoConfiguration::class,
        JdbcTemplateAutoConfiguration::class,
        FlywayAutoConfiguration::class,
    ],
)
@Transactional  // 실제 로컬 DB 를 쓰므로 반드시 롤백한다. 없으면 테스트가 진짜 데이터를 지운다.
class WatchlistJdbcRepositoryIT {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val repository by lazy { WatchlistJdbcRepository(jdbcTemplate) }
    private val now = Instant.parse("2026-09-17T09:00:00Z")

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM watchlist")
    }

    private fun entry(
        symbol: String,
        source: WatchSource,
        category: String = WatchEntry.NO_CATEGORY,
        lastSeenAt: Instant = now,
    ) = WatchEntry(symbol, source, category, addedAt = now, lastSeenAt = lastSeenAt)

    @Test
    fun `같은 종목이 카테고리와 TOP30 에 동시에 존재할 수 있다`() {
        // V1 의 PK(symbol) 로는 불가능했던 것. TOP30 갱신이 카테고리 행을 밀어내면 안 된다.
        repository.upsertAll(
            listOf(
                entry("005930", WatchSource.CATEGORY, "semiconductor"),
                entry("005930", WatchSource.TOP30),
            ),
        )

        assertEquals(listOf("005930"), repository.findDistinctSymbols())
        assertEquals(1, repository.findBySource(WatchSource.CATEGORY).size)
        assertEquals(1, repository.findBySource(WatchSource.TOP30).size)
    }

    @Test
    fun `같은 종목이 여러 카테고리에 속할 수 있다`() {
        repository.upsertAll(
            listOf(
                entry("005930", WatchSource.CATEGORY, "semiconductor"),
                entry("005930", WatchSource.CATEGORY, "largecap"),
            ),
        )

        assertEquals(2, repository.findBySource(WatchSource.CATEGORY).size)
        assertEquals(listOf("005930"), repository.findDistinctSymbols())
    }

    @Test
    fun `다시 넣으면 last_seen_at 만 갱신되고 행은 늘지 않는다`() {
        repository.upsertAll(listOf(entry("005930", WatchSource.TOP30)))
        val later = now.plus(Duration.ofDays(1))

        repository.upsertAll(listOf(entry("005930", WatchSource.TOP30, lastSeenAt = later)))

        val rows = repository.findBySource(WatchSource.TOP30)
        assertEquals(1, rows.size)
        assertEquals(later, rows.single().lastSeenAt)
        assertEquals(now, rows.single().addedAt, "최초 등록 시각은 유지되어야 한다")
    }

    @Test
    fun `deleteStale 은 기준 시각보다 오래된 행만 지운다`() {
        repository.upsertAll(
            listOf(
                entry("000660", WatchSource.TOP30, lastSeenAt = now),
                entry("005930", WatchSource.TOP30, lastSeenAt = now.minus(Duration.ofDays(40))),
                entry("042700", WatchSource.CATEGORY, "semiconductor", lastSeenAt = now.minus(Duration.ofDays(40))),
            ),
        )

        val removed = repository.deleteStale(WatchSource.TOP30, now.minus(Duration.ofDays(30)))

        assertEquals(1, removed)
        assertEquals(listOf("000660"), repository.findBySource(WatchSource.TOP30).map { it.symbol })
        // 오래됐어도 카테고리 행은 유예 정리 대상이 아니다
        assertEquals(1, repository.findBySource(WatchSource.CATEGORY).size)
    }

    @Test
    fun `deleteMissing 은 지정한 조합만 남기고 해당 출처를 정리한다`() {
        repository.upsertAll(
            listOf(
                entry("005930", WatchSource.CATEGORY, "semiconductor"),
                entry("000660", WatchSource.CATEGORY, "semiconductor"),
                entry("005930", WatchSource.TOP30),
            ),
        )

        val removed = repository.deleteMissing(WatchSource.CATEGORY, listOf("005930" to "semiconductor"))

        assertEquals(1, removed)
        assertEquals(listOf("005930"), repository.findBySource(WatchSource.CATEGORY).map { it.symbol })
        assertTrue(repository.findBySource(WatchSource.TOP30).isNotEmpty(), "다른 출처는 건드리면 안 된다")
    }

    @Test
    fun `keep 이 비면 해당 출처를 전부 지운다`() {
        repository.upsertAll(
            listOf(
                entry("005930", WatchSource.CATEGORY, "semiconductor"),
                entry("000660", WatchSource.TOP30),
            ),
        )

        val removed = repository.deleteMissing(WatchSource.CATEGORY, emptyList())

        assertEquals(1, removed)
        assertTrue(repository.findBySource(WatchSource.CATEGORY).isEmpty())
        assertEquals(1, repository.findBySource(WatchSource.TOP30).size)
    }
}
