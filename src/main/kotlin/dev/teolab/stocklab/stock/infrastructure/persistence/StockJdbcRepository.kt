package dev.teolab.stocklab.stock.infrastructure.persistence

import dev.teolab.stocklab.stock.domain.ListingStatus
import dev.teolab.stocklab.stock.domain.Market
import dev.teolab.stocklab.stock.domain.Stock
import dev.teolab.stocklab.stock.domain.StockRepository
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Clock

class StockJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : StockRepository {

    override fun upsertAll(stocks: List<Stock>): Int {
        if (stocks.isEmpty()) return 0
        val updatedAt = Timestamp.from(clock.instant())
        val rows = jdbcTemplate.batchUpdate(
            UPSERT_SQL,
            object : BatchPreparedStatementSetter {
                override fun getBatchSize(): Int = stocks.size

                override fun setValues(ps: PreparedStatement, i: Int) {
                    val stock = stocks[i]
                    ps.setString(1, stock.symbol)
                    ps.setString(2, stock.name)
                    ps.setString(3, stock.market.name)
                    ps.setString(4, stock.securityType)
                    ps.setString(5, stock.isinCode)
                    ps.setString(6, stock.currency)
                    ps.setString(7, stock.listingStatus.name)
                    ps.setTimestamp(8, updatedAt)
                }
            },
        )
        return rows.count { it != 0 }
    }

    override fun markDelisted(markets: Collection<Market>, activeSymbols: Collection<String>): Int {
        if (markets.isEmpty()) return 0
        val marketPlaceholders = markets.joinToString(", ") { "?" }
        val params = mutableListOf<Any>(ListingStatus.DELISTED.name, Timestamp.from(clock.instant()))
        params.addAll(markets.map { it.name })

        // 활성 목록이 비면 그 마켓 전체를 폐지로 찍게 되므로 방어한다.
        // (API 가 일시적으로 빈 목록을 주는 상황에서 전 종목을 죽이면 안 된다)
        if (activeSymbols.isEmpty()) return 0

        val symbolPlaceholders = activeSymbols.joinToString(", ") { "?" }
        params.addAll(activeSymbols)

        return jdbcTemplate.update(
            """
            UPDATE stock
               SET listing_status = ?, updated_at = ?
             WHERE market IN ($marketPlaceholders)
               AND symbol NOT IN ($symbolPlaceholders)
               AND listing_status <> 'DELISTED'
            """.trimIndent(),
            *params.toTypedArray(),
        )
    }

    override fun countByStatus(status: ListingStatus): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM stock WHERE listing_status = ?", Int::class.java, status.name,
        ) ?: 0

    companion object {
        private const val UPSERT_SQL = """
            INSERT INTO stock
                (symbol, name, market, security_type, isin_code, currency, listing_status, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE
                name           = new.name,
                market         = new.market,
                security_type  = new.security_type,
                isin_code      = new.isin_code,
                currency       = new.currency,
                listing_status = new.listing_status,
                updated_at     = new.updated_at
        """
    }
}
