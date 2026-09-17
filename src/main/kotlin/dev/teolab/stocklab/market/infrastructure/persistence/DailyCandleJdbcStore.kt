package dev.teolab.stocklab.market.infrastructure.persistence

import dev.teolab.stocklab.market.domain.DailyCandle
import dev.teolab.stocklab.market.domain.DailyCandleStore
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Clock

/**
 * 일봉 배치 UPSERT.
 *
 * JPA 의 merge 는 건별로 SELECT 를 먼저 날려서 200건 페이지마다 200번 왕복한다.
 * 여기서는 MySQL 8.0.19+ 의 별칭 문법(`AS new ... ON DUPLICATE KEY UPDATE`)으로 한 번에 처리한다.
 * (구문법인 `VALUES(col)` 은 MySQL 8.0.20 부터 deprecated 다.)
 */
class DailyCandleJdbcStore(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : DailyCandleStore {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun upsertAll(candles: List<DailyCandle>): Int {
        if (candles.isEmpty()) return 0
        val collectedAt = Timestamp.from(clock.instant())

        val rows = jdbcTemplate.batchUpdate(
            UPSERT_SQL,
            object : BatchPreparedStatementSetter {
                override fun getBatchSize(): Int = candles.size

                override fun setValues(ps: PreparedStatement, i: Int) {
                    val candle = candles[i]
                    ps.setString(1, candle.symbol)
                    ps.setObject(2, candle.tradeDate)
                    ps.setBigDecimal(3, candle.open)
                    ps.setBigDecimal(4, candle.high)
                    ps.setBigDecimal(5, candle.low)
                    ps.setBigDecimal(6, candle.close)
                    ps.setLong(7, candle.volume)
                    ps.setString(8, candle.currency)
                    ps.setBoolean(9, candle.adjusted)
                    ps.setTimestamp(10, collectedAt)
                }
            },
        )

        val affected = countAffected(rows)
        log.debug("일봉 {}건 UPSERT (영향 {}행)", candles.size, affected)
        return affected
    }

    /**
     * MySQL 은 ON DUPLICATE KEY UPDATE 에서 삽입이면 1, 갱신이면 2, 값이 같아 변화가 없으면 0을 돌려준다.
     * 여기서 알고 싶은 건 "몇 건을 다뤘나"이므로 2도 1로 센다.
     * 배치라 Statement.SUCCESS_NO_INFO(-2) 가 섞일 수 있어 음수도 1건으로 본다.
     */
    private fun countAffected(rows: IntArray): Int = rows.sumOf { row ->
        when {
            row < 0 -> 1
            row >= 2 -> 1
            else -> row
        }
    }

    companion object {
        private const val UPSERT_SQL = """
            INSERT INTO daily_candle
                (symbol, trade_date, open_price, high_price, low_price, close_price,
                 volume, currency, adjusted, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE
                open_price   = new.open_price,
                high_price   = new.high_price,
                low_price    = new.low_price,
                close_price  = new.close_price,
                volume       = new.volume,
                currency     = new.currency,
                adjusted     = new.adjusted,
                collected_at = new.collected_at
        """
    }
}
