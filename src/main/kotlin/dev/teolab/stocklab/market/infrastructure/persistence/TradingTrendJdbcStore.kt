package dev.teolab.stocklab.market.infrastructure.persistence

import dev.teolab.stocklab.market.domain.InvestorTrading
import dev.teolab.stocklab.market.domain.InvestorTradingStore
import dev.teolab.stocklab.market.domain.ShortSelling
import dev.teolab.stocklab.market.domain.ShortSellingStore
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import java.time.Clock

/** 투자자별 매매동향 배치 UPSERT. */
class InvestorTradingJdbcStore(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : InvestorTradingStore {

    override fun upsertAll(records: List<InvestorTrading>): Int {
        if (records.isEmpty()) return 0
        val collectedAt = Timestamp.from(clock.instant())
        val rows = jdbcTemplate.batchUpdate(
            SQL,
            object : BatchPreparedStatementSetter {
                override fun getBatchSize(): Int = records.size

                override fun setValues(ps: PreparedStatement, i: Int) {
                    val record = records[i]
                    ps.setString(1, record.symbol)
                    ps.setObject(2, record.tradeDate)
                    ps.setNullableLong(3, record.individualNet)
                    ps.setNullableLong(4, record.foreignerNet)
                    ps.setNullableLong(5, record.institutionNet)
                    ps.setNullableLong(6, record.otherCorporationNet)
                    ps.setTimestamp(7, collectedAt)
                }
            },
        )
        return rows.count { it != 0 }
    }

    companion object {
        private const val SQL = """
            INSERT INTO investor_trading
                (symbol, trade_date, individual_net, foreign_net, institution_net,
                 other_corporation_net, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE
                individual_net        = new.individual_net,
                foreign_net           = new.foreign_net,
                institution_net       = new.institution_net,
                other_corporation_net = new.other_corporation_net,
                collected_at          = new.collected_at
        """
    }
}

/** 공매도 배치 UPSERT. */
class ShortSellingJdbcStore(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : ShortSellingStore {

    override fun upsertAll(records: List<ShortSelling>): Int {
        if (records.isEmpty()) return 0
        val collectedAt = Timestamp.from(clock.instant())
        val rows = jdbcTemplate.batchUpdate(
            SQL,
            object : BatchPreparedStatementSetter {
                override fun getBatchSize(): Int = records.size

                override fun setValues(ps: PreparedStatement, i: Int) {
                    val record = records[i]
                    ps.setString(1, record.symbol)
                    ps.setObject(2, record.tradeDate)
                    ps.setNullableLong(3, record.volume)
                    ps.setBigDecimal(4, record.amount)
                    ps.setBigDecimal(5, record.volumeRate)
                    ps.setBigDecimal(6, record.amountRate)
                    ps.setTimestamp(7, collectedAt)
                }
            },
        )
        return rows.count { it != 0 }
    }

    companion object {
        private const val SQL = """
            INSERT INTO short_selling
                (symbol, trade_date, short_volume, short_amount, short_volume_rate,
                 short_amount_rate, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE
                short_volume      = new.short_volume,
                short_amount      = new.short_amount,
                short_volume_rate = new.short_volume_rate,
                short_amount_rate = new.short_amount_rate,
                collected_at      = new.collected_at
        """
    }
}

/** 수급은 값이 없는 주체가 흔해서 null 을 자주 다룬다. */
private fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
    if (value == null) setNull(index, Types.BIGINT) else setLong(index, value)
}
