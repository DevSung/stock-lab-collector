package dev.teolab.stocklab.market.infrastructure.persistence

import dev.teolab.stocklab.market.domain.DailyCandle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
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

/**
 * 실제 MySQL 에 붙는 통합 테스트. `docker compose up -d` 가 떠 있어야 한다.
 *
 * UPSERT 는 H2 로 대체 검증할 수 없다. MySQL 전용 문법(`AS new ... ON DUPLICATE KEY UPDATE`)이라
 * 진짜 MySQL 에 붙지 않으면 검증하는 의미가 없다.
 */
@SpringBootTest(
    classes = [
        DataSourceAutoConfiguration::class,
        DataSourceTransactionManagerAutoConfiguration::class,
        JdbcTemplateAutoConfiguration::class,
        FlywayAutoConfiguration::class,
    ],
)
@Transactional  // 실제 로컬 DB 를 쓰므로 반드시 롤백한다. 없으면 테스트가 진짜 데이터를 지운다.
class DailyCandleJdbcStoreIT {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val collectedAt = Instant.parse("2026-09-17T10:00:00Z")
    private val store by lazy { DailyCandleJdbcStore(jdbcTemplate, Clock.fixed(collectedAt, ZoneOffset.UTC)) }

    private val symbol = "TEST01"

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM daily_candle WHERE symbol = ?", symbol)
    }

    private fun candle(date: LocalDate, close: String, volume: Long = 1_000L) = DailyCandle(
        symbol = symbol,
        tradeDate = date,
        open = BigDecimal("100"), high = BigDecimal("110"), low = BigDecimal("90"),
        close = BigDecimal(close), volume = volume, currency = "KRW", adjusted = true,
    )

    private fun closeOf(date: LocalDate): BigDecimal = jdbcTemplate.queryForObject(
        "SELECT close_price FROM daily_candle WHERE symbol = ? AND trade_date = ?",
        BigDecimal::class.java, symbol, date,
    )!!

    private fun countRows(): Int = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM daily_candle WHERE symbol = ?", Int::class.java, symbol,
    )!!

    @Test
    fun `새 봉은 삽입된다`() {
        val saved = store.upsertAll(
            listOf(
                candle(LocalDate.of(2026, 9, 17), "256000"),
                candle(LocalDate.of(2026, 9, 16), "253500"),
            ),
        )

        assertEquals(2, saved)
        assertEquals(2, countRows())
        assertEquals(0, BigDecimal("256000").compareTo(closeOf(LocalDate.of(2026, 9, 17))))
    }

    @Test
    fun `같은 종목 같은 날짜를 다시 넣으면 덮어쓴다`() {
        // 액면분할 등으로 수정주가가 재계산된 상황. 값이 바뀐 채로 다시 들어온다.
        val date = LocalDate.of(2026, 9, 17)
        store.upsertAll(listOf(candle(date, "256000", volume = 11_058_213L)))

        store.upsertAll(listOf(candle(date, "51200", volume = 55_291_065L)))

        assertEquals(1, countRows(), "행이 늘어나면 안 된다")
        assertEquals(0, BigDecimal("51200").compareTo(closeOf(date)))
        val volume = jdbcTemplate.queryForObject(
            "SELECT volume FROM daily_candle WHERE symbol = ? AND trade_date = ?",
            Long::class.java, symbol, date,
        )
        assertEquals(55_291_065L, volume)
    }

    @Test
    fun `재수집은 기존 행과 새 행을 함께 처리한다`() {
        store.upsertAll(listOf(candle(LocalDate.of(2026, 9, 17), "256000")))

        val saved = store.upsertAll(
            listOf(
                candle(LocalDate.of(2026, 9, 17), "255000"),
                candle(LocalDate.of(2026, 9, 16), "253500"),
            ),
        )

        assertEquals(2, saved)
        assertEquals(2, countRows())
        assertEquals(0, BigDecimal("255000").compareTo(closeOf(LocalDate.of(2026, 9, 17))))
    }

    @Test
    fun `빈 목록은 쿼리를 날리지 않는다`() {
        assertEquals(0, store.upsertAll(emptyList()))
        assertEquals(0, countRows())
    }
}
