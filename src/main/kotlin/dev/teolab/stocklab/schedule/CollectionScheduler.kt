package dev.teolab.stocklab.schedule

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 스케줄 트리거만 담당한다. 실제 작업은 [DailyCollectionJob] 에 있다.
 *
 * 기본 스케줄러 스레드 풀은 1개다. 일부러 늘리지 않는다.
 * 토스 토큰은 클라이언트당 1개뿐이고 rate limit 도 빡빡해서, 배치끼리 동시에 도는 것보다
 * 순서대로 줄 서는 편이 안전하다. 일일 수집이 길어지면 다음 작업은 기다렸다 돈다.
 *
 * `stocklab.schedule.enabled=true` 일 때만 등록된다. CLI 로 한 번씩 돌릴 때
 * 스케줄러까지 같이 깨어나는 일을 막기 위해 기본값은 꺼짐이다.
 */
@Component
@ConditionalOnProperty(prefix = "stocklab.schedule", name = ["enabled"], havingValue = "true")
class CollectionScheduler(
    private val job: DailyCollectionJob,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${stocklab.schedule.stock-master-cron}", zone = "\${stocklab.schedule.zone}")
    fun syncStockMaster() = runSafely("종목 마스터 동기화") { job.syncStockMaster() }

    @Scheduled(cron = "\${stocklab.schedule.top30-cron}", zone = "\${stocklab.schedule.zone}")
    fun refreshWatchlist() = runSafely("watchlist 갱신") { job.refreshWatchlist() }

    @Scheduled(cron = "\${stocklab.schedule.daily-collect-cron}", zone = "\${stocklab.schedule.zone}")
    fun collectDaily() = runSafely("일일 수집") { job.collectDaily() }

    /**
     * 스케줄 메서드에서 예외가 새어나가면 그 작업이 **다음부터 아예 안 돈다**(Spring 이 등록을 취소한다).
     * 실패는 collection_log 와 로그에 남기고 스케줄은 살려둔다.
     */
    private fun runSafely(label: String, block: () -> Any?) {
        runCatching(block)
            .onSuccess { log.info("[스케줄] {} 성공: {}", label, it) }
            .onFailure { log.error("[스케줄] {} 실패 — 다음 주기에 재시도한다", label, it) }
    }
}
