package dev.teolab.stocklab.watchlist.infrastructure.persistence

import dev.teolab.stocklab.watchlist.domain.RankingSnapshot
import dev.teolab.stocklab.watchlist.domain.Top30HistoryStore
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import java.time.Clock
import java.time.ZoneId

class Top30HistoryJdbcStore(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
) : Top30HistoryStore {

    override fun save(snapshot: RankingSnapshot): Int {
        if (snapshot.isEmpty) return 0
        // rankedAt 은 집계 시각(장중일 수도 있다). 거래일은 KST 날짜로 뽑는다.
        val tradeDate = snapshot.rankedAt.atZoneSameInstant(zone).toLocalDate()
        val rankedAt = Timestamp.from(snapshot.rankedAt.toInstant())
        val collectedAt = Timestamp.from(clock.instant())
        val entries = snapshot.entries

        val rows = jdbcTemplate.batchUpdate(
            UPSERT_SQL,
            object : BatchPreparedStatementSetter {
                override fun getBatchSize(): Int = entries.size

                override fun setValues(ps: PreparedStatement, i: Int) {
                    val entry = entries[i]
                    ps.setObject(1, tradeDate)
                    ps.setInt(2, entry.rank)
                    ps.setString(3, entry.symbol)
                    ps.setBigDecimal(4, entry.tradingAmount)
                    entry.tradingVolume?.let { ps.setLong(5, it) } ?: ps.setNull(5, Types.BIGINT)
                    ps.setBigDecimal(6, entry.lastPrice)
                    ps.setBigDecimal(7, entry.changeRate)
                    ps.setTimestamp(8, rankedAt)
                    ps.setTimestamp(9, collectedAt)
                }
            },
        )
        return rows.count { it != 0 }
    }

    companion object {
        private const val UPSERT_SQL = """
            INSERT INTO top30_daily
                (trade_date, rank_no, symbol, trading_amount, trading_volume,
                 last_price, change_rate, ranked_at, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE
                symbol         = new.symbol,
                trading_amount = new.trading_amount,
                trading_volume = new.trading_volume,
                last_price     = new.last_price,
                change_rate    = new.change_rate,
                ranked_at      = new.ranked_at,
                collected_at   = new.collected_at
        """
    }
}
