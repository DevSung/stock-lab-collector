package dev.teolab.stocklab.stock.infrastructure.persistence

import dev.teolab.stocklab.stock.domain.ListingStatus
import dev.teolab.stocklab.stock.domain.Market
import dev.teolab.stocklab.stock.domain.Stock
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

/**
 * 실제 MySQL 통합 테스트. `docker compose up -d` 가 떠 있어야 한다.
 *
 * 적재를 손으로 쓴 SQL 로 하므로, 컬럼명·타입이 마이그레이션과 맞는지는
 * 진짜 MySQL 에 날려보는 것 말고 검증할 방법이 없다.
 */
@SpringBootTest(
    classes = [
        DataSourceAutoConfiguration::class,
        DataSourceTransactionManagerAutoConfiguration::class,
        JdbcTemplateAutoConfiguration::class,
        FlywayAutoConfiguration::class,
    ],
)
@Transactional  // 실제 로컬 DB 를 쓰므로 반드시 롤백한다.
class StockJdbcRepositoryIT {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val repository by lazy {
        StockJdbcRepository(jdbcTemplate, Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC))
    }

    /**
     * 실제 동기화는 KOSPI/KOSDAQ 만 채운다(Market.DOMESTIC).
     * markDelisted 는 마켓 전체를 훑으므로, 실데이터가 없는 마켓을 써서 격리한다.
     * KOSPI 로 하면 진짜 종목 2481개가 판정 범위에 들어온다.
     */
    private val testMarket = Market.KR_ETC
    private val otherMarket = Market.US_ETC

    private fun stock(
        symbol: String,
        name: String = "테스트종목",
        market: Market = testMarket,
        status: ListingStatus = ListingStatus.ACTIVE,
    ) = Stock(symbol, name, market, market.currency, status, "STOCK", "KR$symbol")

    private fun statusOf(symbol: String): String? = jdbcTemplate.queryForObject(
        "SELECT listing_status FROM stock WHERE symbol = ?", String::class.java, symbol,
    )

    @Test
    fun `종목을 저장하고 이름 변경을 반영한다`() {
        repository.upsertAll(listOf(stock("TST001", name = "옛이름")))

        repository.upsertAll(listOf(stock("TST001", name = "새이름")))

        val row = jdbcTemplate.queryForMap("SELECT * FROM stock WHERE symbol = ?", "TST001")
        assertEquals("새이름", row["name"])
        assertEquals(testMarket.name, row["market"])
        assertEquals("KRW", row["currency"])
        assertEquals("STOCK", row["security_type"])
        assertEquals("KRTST001", row["isin_code"])
    }

    @Test
    fun `활성 목록에 없는 종목만 폐지로 표시한다`() {
        repository.upsertAll(listOf(stock("TST001"), stock("TST002"), stock("TST003", market = otherMarket)))

        repository.markDelisted(listOf(testMarket), listOf("TST001"))

        assertEquals("ACTIVE", statusOf("TST001"))
        assertEquals("DELISTED", statusOf("TST002"), "KOSPI 활성 목록에 없으니 폐지여야 한다")
        assertEquals("ACTIVE", statusOf("TST003"), "다른 마켓은 건드리면 안 된다")
    }

    @Test
    fun `활성 목록이 비면 아무것도 폐지시키지 않는다`() {
        repository.upsertAll(listOf(stock("TST001"), stock("TST002")))

        val marked = repository.markDelisted(listOf(testMarket), emptyList())

        // API 가 일시적으로 빈 목록을 줬을 때 전 종목을 죽이면 안 된다
        assertEquals(0, marked)
        assertEquals("ACTIVE", statusOf("TST001"))
    }

    @Test
    fun `이미 폐지된 종목은 다시 표시하지 않는다`() {
        repository.upsertAll(listOf(stock("TST001", status = ListingStatus.DELISTED)))

        val marked = repository.markDelisted(listOf(testMarket), listOf("OTHER"))

        // WHERE 절의 listing_status <> 'DELISTED' 가 없으면 이미 폐지된 행도 다시 UPDATE 된다
        assertEquals(0, marked)
    }

    @Test
    fun `빈 목록은 쿼리를 날리지 않는다`() {
        assertEquals(0, repository.upsertAll(emptyList()))
        assertEquals(0, repository.markDelisted(emptyList(), listOf("TST001")))
    }
}
