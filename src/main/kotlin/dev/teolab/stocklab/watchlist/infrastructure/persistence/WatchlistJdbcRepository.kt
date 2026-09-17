package dev.teolab.stocklab.watchlist.infrastructure.persistence

import dev.teolab.stocklab.watchlist.domain.WatchEntry
import dev.teolab.stocklab.watchlist.domain.WatchSource
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant

class WatchlistJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) : WatchlistRepository {

    override fun upsertAll(entries: List<WatchEntry>): Int {
        if (entries.isEmpty()) return 0
        val rows = jdbcTemplate.batchUpdate(
            UPSERT_SQL,
            object : BatchPreparedStatementSetter {
                override fun getBatchSize(): Int = entries.size

                override fun setValues(ps: PreparedStatement, i: Int) {
                    val entry = entries[i]
                    ps.setString(1, entry.symbol)
                    ps.setString(2, entry.source.name)
                    ps.setString(3, entry.category)
                    ps.setTimestamp(4, Timestamp.from(entry.addedAt))
                    ps.setTimestamp(5, Timestamp.from(entry.lastSeenAt))
                }
            },
        )
        // 삽입 1 / 갱신 2 / 변화 없음 0 / 배치 미상 -2 — 여기서는 "다룬 건수"만 알면 된다
        return rows.count { it != 0 }
    }

    override fun findDistinctSymbols(): List<String> =
        jdbcTemplate.queryForList("SELECT DISTINCT symbol FROM watchlist ORDER BY symbol", String::class.java)
            .filterNotNull()

    override fun findBySource(source: WatchSource): List<WatchEntry> =
        jdbcTemplate.query(
            "SELECT symbol, source, category, added_at, last_seen_at FROM watchlist WHERE source = ? ORDER BY symbol",
            { rs, _ ->
                WatchEntry(
                    symbol = rs.getString("symbol"),
                    source = WatchSource.from(rs.getString("source")),
                    category = rs.getString("category"),
                    addedAt = rs.getTimestamp("added_at").toInstant(),
                    lastSeenAt = rs.getTimestamp("last_seen_at").toInstant(),
                )
            },
            source.name,
        )

    override fun deleteStale(source: WatchSource, threshold: Instant): Int =
        jdbcTemplate.update(
            "DELETE FROM watchlist WHERE source = ? AND last_seen_at < ?",
            source.name,
            Timestamp.from(threshold),
        )

    override fun deleteMissing(source: WatchSource, keep: Collection<Pair<String, String>>): Int {
        if (keep.isEmpty()) {
            return jdbcTemplate.update("DELETE FROM watchlist WHERE source = ?", source.name)
        }
        val placeholders = keep.joinToString(", ") { "(?, ?)" }
        val params = buildList {
            add(source.name)
            keep.forEach { (symbol, category) -> add(symbol); add(category) }
        }
        return jdbcTemplate.update(
            "DELETE FROM watchlist WHERE source = ? AND (symbol, category) NOT IN ($placeholders)",
            *params.toTypedArray(),
        )
    }

    companion object {
        private const val UPSERT_SQL = """
            INSERT INTO watchlist (symbol, source, category, added_at, last_seen_at)
            VALUES (?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE last_seen_at = new.last_seen_at
        """
    }
}
