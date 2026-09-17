package dev.teolab.stocklab.bootstrap

import dev.teolab.stocklab.collection.domain.CollectionLogRepository
import dev.teolab.stocklab.schedule.DailyCollectionJob
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 스케줄러가 도는 것과 **똑같은 작업**을 지금 한 번 돌린다.
 * 새벽까지 기다리지 않고 배치를 검증할 수 있고, 수동 재처리 수단도 된다.
 *
 *   ./scripts/run.sh --spring.profiles.active=job --job=daily
 *   ./scripts/run.sh --spring.profiles.active=job --job=watchlist
 *   ./scripts/run.sh --spring.profiles.active=job --job=master
 *   ./scripts/run.sh --spring.profiles.active=job --job=log
 */
@Component
@Profile("job & !test")  // 테스트 컨텍스트에서는 실행되지 않게 한다
class JobRunner(
    private val job: DailyCollectionJob,
    private val logRepository: CollectionLogRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        when (val name = args.getOptionValues("job")?.firstOrNull() ?: "daily") {
            "master" -> println("종목 마스터: ${job.syncStockMaster()}")
            "watchlist" -> println("watchlist: ${job.refreshWatchlist()}")
            "daily" -> println("일일 수집: ${job.collectDaily()}")
            "log" -> printRecentLogs()
            else -> error("알 수 없는 job: $name (master | watchlist | daily | log)")
        }
        if (args.getOptionValues("job")?.firstOrNull() != "log") printRecentLogs()
    }

    private fun printRecentLogs() {
        println()
        println("최근 수집 이력")
        println("─".repeat(96))
        println("%-20s %-10s %-22s %-8s %s".format("작업", "상태", "시작", "소요", "결과"))
        println("─".repeat(96))
        logRepository.findRecent(RECENT_LIMIT).forEach { row ->
            val elapsed = row.finishedAt?.let { Duration.between(row.startedAt, it) }
            println(
                "%-20s %-10s %-22s %-8s %s".format(
                    row.jobName,
                    row.status,
                    row.startedAt.toString().take(19),
                    elapsed?.let { "%.1f초".format(it.toMillis() / 1000.0) } ?: "-",
                    (row.message ?: "").take(50),
                ),
            )
        }
        println()
    }

    companion object {
        private const val RECENT_LIMIT = 10
    }
}
