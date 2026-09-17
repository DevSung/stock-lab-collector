package dev.teolab.stocklab.collection.application

import dev.teolab.stocklab.collection.domain.CollectionLogRepository
import dev.teolab.stocklab.collection.domain.JobStatus
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * 배치 실행을 collection_log 에 남기면서 돌린다.
 *
 * 스케줄러가 새벽에 조용히 실패하면 로그 파일을 뒤지기 전엔 알 수 없다.
 * 성공이든 실패든 DB 에 한 줄 남겨두면 "어제 수집이 됐나"를 SQL 한 줄로 확인할 수 있다.
 *
 * 이력 기록 자체가 실패해도 본 작업은 계속 진행한다. 기록은 곁다리지 목적이 아니다.
 */
class JobRecorder(
    private val repository: CollectionLogRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T> record(jobName: String, symbol: String? = null, targetRange: String? = null, block: () -> T): T {
        val startedAt = clock.instant()
        val id = runCatching { repository.start(jobName, symbol, targetRange, startedAt) }
            .onFailure { log.warn("수집 이력 시작 기록 실패 ({}): {}", jobName, it.message) }
            .getOrNull()

        return try {
            val result = block()
            id?.let { finish(it, JobStatus.SUCCESS, summarize(result)) }
            result
        } catch (e: Exception) {
            id?.let { finish(it, JobStatus.FAILED, "${e::class.simpleName}: ${e.message}") }
            throw e
        }
    }

    private fun finish(id: Long, status: JobStatus, message: String?) {
        runCatching { repository.finish(id, status, message?.take(MESSAGE_LIMIT), clock.instant()) }
            .onFailure { log.warn("수집 이력 종료 기록 실패: {}", it.message) }
    }

    private fun summarize(result: Any?): String? = result?.toString()

    companion object {
        private const val MESSAGE_LIMIT = 1000
    }
}
