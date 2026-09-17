package dev.teolab.stocklab.bootstrap

import dev.teolab.stocklab.market.application.TradingTrendCollector
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 수급 수집 확인용(5단계).
 *
 *   ./scripts/run.sh --spring.profiles.active=trend --symbol=005930 --from=2026-08-01
 *   ./scripts/run.sh --spring.profiles.active=trend --watchlist --days=30
 *
 * --watchlist 를 주면 watchlist 테이블의 모든 종목을 돈다. rate limiter 가 실제로 페이싱하는지 볼 수 있다.
 */
@Component
@Profile("trend & !test")  // 테스트 컨텍스트에서는 실행되지 않게 한다
class TrendCollectRunner(
    private val collector: TradingTrendCollector,
    private val watchlistRepository: WatchlistRepository,
    private val clock: Clock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val today = LocalDate.ofInstant(clock.instant(), KST)
        val symbols = when {
            args.containsOption("watchlist") -> watchlistRepository.findDistinctSymbols()
            else -> args.getOptionValues("symbol").orEmpty()
        }
        require(symbols.isNotEmpty()) { "--symbol=005930 또는 --watchlist 를 지정해야 한다" }

        val days = args.single("days")?.toLongOrNull() ?: DEFAULT_DAYS
        val from = args.single("from")?.let(LocalDate::parse) ?: today.minusDays(days)
        val to = args.single("to")?.let(LocalDate::parse) ?: today

        println("수급 수집 %s ~ %s · %d종목".format(from, to, symbols.size))
        println("─".repeat(72))
        val startedAt = System.currentTimeMillis()

        symbols.forEach { symbol ->
            val investor = collector.collectInvestorTrading(symbol, from, to)
            val short = collector.collectShortSelling(symbol, from, to)
            println(
                "%-8s  투자자 %3d건(%d페이지)  공매도 %3d건(%d페이지)".format(
                    symbol, investor.saved, investor.pages, short.saved, short.pages,
                ),
            )
        }

        println("─".repeat(72))
        println("소요 %.1f초 · API 호출 %d회 이상".format((System.currentTimeMillis() - startedAt) / 1000.0, symbols.size * 2))
    }

    private fun ApplicationArguments.single(name: String): String? = getOptionValues(name)?.firstOrNull()

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private const val DEFAULT_DAYS = 30L
    }
}
