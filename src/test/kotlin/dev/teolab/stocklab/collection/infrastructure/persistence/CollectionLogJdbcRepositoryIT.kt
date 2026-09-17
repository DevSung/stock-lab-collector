package dev.teolab.stocklab.collection.infrastructure.persistence

import dev.teolab.stocklab.collection.domain.JobStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
class CollectionLogJdbcRepositoryIT {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val repository by lazy { CollectionLogJdbcRepository(jdbcTemplate) }
    private val startedAt = Instant.parse("2026-09-17T09:00:00Z")

    @Test
    fun `시작을 기록하고 자동증가 id 를 돌려준다`() {
        val id = repository.start("test-job", "005930", "2026-09-01~2026-09-17", startedAt)

        assertTrue(id > 0, "AUTO_INCREMENT 키를 받아오지 못했다")
        val row = jdbcTemplate.queryForMap("SELECT * FROM collection_log WHERE id = ?", id)
        assertEquals("test-job", row["job_name"])
        assertEquals("005930", row["symbol"])
        assertEquals("2026-09-01~2026-09-17", row["target_range"])
        assertEquals(JobStatus.RUNNING.name, row["status"])
        assertNull(row["finished_at"], "시작 시점에는 종료 시각이 없어야 한다")
    }

    @Test
    fun `종료 시 상태와 메시지를 채운다`() {
        val id = repository.start("test-job", null, null, startedAt)

        repository.finish(id, JobStatus.SUCCESS, "종목 34 · 일봉 782", startedAt.plusSeconds(15))

        val row = jdbcTemplate.queryForMap("SELECT * FROM collection_log WHERE id = ?", id)
        assertEquals(JobStatus.SUCCESS.name, row["status"])
        assertEquals("종목 34 · 일봉 782", row["message"])
        assertNotNull(row["finished_at"])
    }

    @Test
    fun `실패도 사유와 함께 남는다`() {
        val id = repository.start("test-job", null, null, startedAt)

        repository.finish(id, JobStatus.FAILED, "TossServerException: 502", startedAt.plusSeconds(3))

        val recent = repository.findRecent(20).single { it.id == id }
        assertEquals(JobStatus.FAILED, recent.status)
        assertTrue(recent.message!!.contains("502"))
    }

    @Test
    fun `findRecent 는 최신순으로 준다`() {
        val older = repository.start("job-a", null, null, startedAt)
        val newer = repository.start("job-b", null, null, startedAt.plusSeconds(60))

        val ids = repository.findRecent(50).map { it.id }

        assertTrue(ids.indexOf(newer) < ids.indexOf(older), "최신 것이 앞에 와야 한다")
    }

    @Test
    fun `symbol 과 target_range 는 없어도 된다`() {
        val id = repository.start("test-job", null, null, startedAt)

        val row = repository.findRecent(20).single { it.id == id }
        assertNull(row.symbol)
        assertNull(row.targetRange)
    }
}
