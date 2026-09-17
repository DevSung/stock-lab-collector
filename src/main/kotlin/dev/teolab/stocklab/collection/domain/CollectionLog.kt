package dev.teolab.stocklab.collection.domain

import java.time.Instant

/** 수집 작업 한 건의 이력. */
data class CollectionLog(
    val id: Long?,
    val jobName: String,
    val symbol: String?,
    val targetRange: String?,
    val status: JobStatus,
    val message: String?,
    val startedAt: Instant,
    val finishedAt: Instant?,
)

enum class JobStatus {
    RUNNING,
    SUCCESS,
    FAILED,
}

/** 수집 이력 저장 포트. */
interface CollectionLogRepository {
    /** 시작을 기록하고 id 를 돌려준다. */
    fun start(jobName: String, symbol: String?, targetRange: String?, startedAt: Instant): Long

    fun finish(id: Long, status: JobStatus, message: String?, finishedAt: Instant)

    fun findRecent(limit: Int): List<CollectionLog>
}
