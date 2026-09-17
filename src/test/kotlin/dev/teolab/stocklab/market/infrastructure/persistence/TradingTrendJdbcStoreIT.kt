package dev.teolab.stocklab.market.infrastructure.persistence

import dev.teolab.stocklab.market.domain.InvestorTrading
import dev.teolab.stocklab.market.domain.ShortSelling
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
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
class TradingTrendJdbcStoreIT {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val clock = Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC)
    private val investorStore by lazy { InvestorTradingJdbcStore(jdbcTemplate, clock) }
    private val shortStore by lazy { ShortSellingJdbcStore(jdbcTemplate, clock) }

    private val symbol = "TEST02"
    private val date = LocalDate.of(2026, 9, 16)

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM investor_trading WHERE symbol = ?", symbol)
        jdbcTemplate.update("DELETE FROM short_selling WHERE symbol = ?", symbol)
    }

    @Test
    fun `투자자별 순매수를 저장한다`() {
        val saved = investorStore.upsertAll(
            listOf(InvestorTrading(symbol, date, -2_506_478L, -1_681_512L, 2_219_531L, 12_345L)),
        )

        assertEquals(1, saved)
        val row = jdbcTemplate.queryForMap(
            "SELECT * FROM investor_trading WHERE symbol = ? AND trade_date = ?", symbol, date,
        )
        assertEquals(-2_506_478L, row["individual_net"])
        assertEquals(-1_681_512L, row["foreign_net"])
        assertEquals(2_219_531L, row["institution_net"])
    }

    @Test
    fun `장 마감 전이라 비어 있는 주체는 null 로 저장된다`() {
        // 실측: 당일 레코드는 individual 과 otherCorporation 이 null 로 온다
        investorStore.upsertAll(listOf(InvestorTrading(symbol, date, null, -718_171L, -59_000L, null)))

        val row = jdbcTemplate.queryForMap(
            "SELECT * FROM investor_trading WHERE symbol = ? AND trade_date = ?", symbol, date,
        )
        assertNull(row["individual_net"])
        assertNull(row["other_corporation_net"])
        assertEquals(-718_171L, row["foreign_net"])
    }

    @Test
    fun `나중에 확정된 값이 오면 덮어쓴다`() {
        investorStore.upsertAll(listOf(InvestorTrading(symbol, date, null, -718_171L, -59_000L, null)))

        investorStore.upsertAll(listOf(InvestorTrading(symbol, date, -2_506_478L, -1_681_512L, 2_219_531L, 500L)))

        val count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM investor_trading WHERE symbol = ?", Int::class.java, symbol,
        )
        assertEquals(1, count, "행이 늘어나면 안 된다")
        val row = jdbcTemplate.queryForMap(
            "SELECT * FROM investor_trading WHERE symbol = ? AND trade_date = ?", symbol, date,
        )
        assertEquals(-2_506_478L, row["individual_net"], "null 이던 값이 채워져야 한다")
    }

    @Test
    fun `공매도는 거래량 비중과 거래대금 비중을 모두 저장한다`() {
        shortStore.upsertAll(
            listOf(
                ShortSelling(
                    symbol, date,
                    volume = 615_534L,
                    amount = BigDecimal("154876820750"),
                    volumeRate = BigDecimal("0.05235"),
                    amountRate = BigDecimal("0.0524"),
                ),
            ),
        )

        val row = jdbcTemplate.queryForMap(
            "SELECT * FROM short_selling WHERE symbol = ? AND trade_date = ?", symbol, date,
        )
        assertEquals(615_534L, row["short_volume"])
        // 소수 5자리가 반올림으로 뭉개지면 안 된다 (V3 에서 scale 을 6 으로 넓힌 이유)
        assertEquals(0, BigDecimal("0.05235").compareTo(row["short_volume_rate"] as BigDecimal))
        assertEquals(0, BigDecimal("0.0524").compareTo(row["short_amount_rate"] as BigDecimal))
    }

    @Test
    fun `빈 목록은 쿼리를 날리지 않는다`() {
        assertEquals(0, investorStore.upsertAll(emptyList()))
        assertEquals(0, shortStore.upsertAll(emptyList()))
    }
}
