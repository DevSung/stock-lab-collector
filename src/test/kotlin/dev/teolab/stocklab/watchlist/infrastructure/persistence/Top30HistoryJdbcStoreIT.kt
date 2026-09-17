package dev.teolab.stocklab.watchlist.infrastructure.persistence

import dev.teolab.stocklab.watchlist.domain.RankingEntry
import dev.teolab.stocklab.watchlist.domain.RankingSnapshot
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

/** 실제 MySQL 통합 테스트. `docker compose up -d` 가 떠 있어야 한다. */
@SpringBootTest(
    classes = [
        DataSourceAutoConfiguration::class,
        DataSourceTransactionManagerAutoConfiguration::class,
        JdbcTemplateAutoConfiguration::class,
        FlywayAutoConfiguration::class,
    ],
)
@Transactional
class Top30HistoryJdbcStoreIT {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val store by lazy {
        Top30HistoryJdbcStore(jdbcTemplate, Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC))
    }

    /** KST 자정 직후 시각. UTC 로 잘못 변환하면 하루 밀린다. */
    private val rankedAt = OffsetDateTime.parse("2026-01-02T00:30:00+09:00")

    private fun snapshot(vararg symbols: String) = RankingSnapshot(
        rankedAt = rankedAt,
        entries = symbols.mapIndexed { i, s ->
            RankingEntry(
                rank = i + 1,
                symbol = s,
                tradingAmount = BigDecimal("5364181931000"),
                tradingVolume = 3_055_099L,
                lastPrice = BigDecimal("1756000"),
                changeRate = BigDecimal("-0.001700"),
            )
        },
    )

    private fun rowsOn(date: LocalDate): List<Map<String, Any?>> = jdbcTemplate.queryForList(
        "SELECT * FROM top30_daily WHERE trade_date = ? ORDER BY rank_no", date,
    )

    @Test
    fun `순위를 거래일별로 저장한다`() {
        val saved = store.save(snapshot("000660", "005930", "069500"))

        assertEquals(3, saved)
        val rows = rowsOn(LocalDate.of(2026, 1, 2))
        assertEquals(listOf(1, 2, 3), rows.map { (it["rank_no"] as Number).toInt() })
        assertEquals(listOf("000660", "005930", "069500"), rows.map { it["symbol"] })
        assertEquals(0, BigDecimal("5364181931000").compareTo(rows[0]["trading_amount"] as BigDecimal))
        // 음수 등락률이 부호까지 보존되는지 (DECIMAL(9,6))
        assertEquals(0, BigDecimal("-0.001700").compareTo(rows[0]["change_rate"] as BigDecimal))
    }

    @Test
    fun `거래일은 KST 기준으로 뽑는다`() {
        store.save(snapshot("000660"))

        // rankedAt 이 1/2 00:30 KST = 1/1 15:30 UTC. UTC 로 변환하면 1/1 이 된다.
        assertEquals(1, rowsOn(LocalDate.of(2026, 1, 2)).size)
        assertEquals(0, rowsOn(LocalDate.of(2026, 1, 1)).size, "UTC 로 변환되면 하루 밀린다")
    }

    @Test
    fun `같은 날 다시 돌려도 행이 늘지 않고 갱신된다`() {
        store.save(snapshot("000660", "005930"))

        store.save(snapshot("005930", "000660"))

        val rows = rowsOn(LocalDate.of(2026, 1, 2))
        assertEquals(2, rows.size, "장중에 여러 번 돌려도 행이 늘면 안 된다")
        assertEquals(listOf("005930", "000660"), rows.map { it["symbol"] }, "순위가 바뀌면 덮어써야 한다")
    }

    @Test
    fun `빈 스냅샷은 쿼리를 날리지 않는다`() {
        assertEquals(0, store.save(RankingSnapshot(rankedAt, emptyList())))
    }
}
