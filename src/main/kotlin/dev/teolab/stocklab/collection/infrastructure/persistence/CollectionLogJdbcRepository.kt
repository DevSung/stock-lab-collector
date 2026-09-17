package dev.teolab.stocklab.collection.infrastructure.persistence

import dev.teolab.stocklab.collection.domain.CollectionLog
import dev.teolab.stocklab.collection.domain.CollectionLogRepository
import dev.teolab.stocklab.collection.domain.JobStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant

class CollectionLogJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) : CollectionLogRepository {

    override fun start(jobName: String, symbol: String?, targetRange: String?, startedAt: Instant): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS).apply {
                setString(1, jobName)
                setString(2, symbol)
                setString(3, targetRange)
                setString(4, JobStatus.RUNNING.name)
                setTimestamp(5, Timestamp.from(startedAt))
            }
        }, keyHolder)
        return requireNotNull(keyHolder.key) { "collection_log id 를 받지 못했다" }.toLong()
    }

    override fun finish(id: Long, status: JobStatus, message: String?, finishedAt: Instant) {
        jdbcTemplate.update(
            "UPDATE collection_log SET status = ?, message = ?, finished_at = ? WHERE id = ?",
            status.name, message, Timestamp.from(finishedAt), id,
        )
    }

    override fun findRecent(limit: Int): List<CollectionLog> =
        jdbcTemplate.query(
            "SELECT * FROM collection_log ORDER BY started_at DESC, id DESC LIMIT ?",
            { rs, _ ->
                CollectionLog(
                    id = rs.getLong("id"),
                    jobName = rs.getString("job_name"),
                    symbol = rs.getString("symbol"),
                    targetRange = rs.getString("target_range"),
                    status = JobStatus.valueOf(rs.getString("status")),
                    message = rs.getString("message"),
                    startedAt = rs.getTimestamp("started_at").toInstant(),
                    finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                )
            },
            limit,
        )

    companion object {
        private const val INSERT_SQL = """
            INSERT INTO collection_log (job_name, symbol, target_range, status, started_at)
            VALUES (?, ?, ?, ?, ?)
        """
    }
}
