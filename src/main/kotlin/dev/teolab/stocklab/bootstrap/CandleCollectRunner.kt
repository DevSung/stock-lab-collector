package dev.teolab.stocklab.bootstrap

import dev.teolab.stocklab.market.application.DailyCandleCollector
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 과거 일봉 초기 적재 / 기간 재수집 CLI.
 *
 *   ./scripts/run.sh --spring.profiles.active=collect --symbol=005930 --from=2024-01-01
 *   ./scripts/run.sh --spring.profiles.active=collect --symbol=005930 --from=2020-01-01 --to=2020-12-31
 *
 * 같은 기간을 다시 돌리면 덮어쓴다. 액면분할로 수정주가가 재계산됐을 때 이렇게 복구한다.
 */
@Component
@Profile("collect & !test")  // 테스트 컨텍스트에서는 실행되지 않게 한다
class CandleCollectRunner(
    private val collector: DailyCandleCollector,
    private val clock: Clock,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val today = LocalDate.ofInstant(clock.instant(), KST)
        val symbols = args.getOptionValues("symbol").orEmpty()
        require(symbols.isNotEmpty()) { "--symbol=005930 형태로 종목을 지정해야 한다" }

        val from = args.single("from")?.let(LocalDate::parse)
            ?: today.minusDays(DEFAULT_DAYS)
        val to = args.single("to")?.let(LocalDate::parse) ?: today

        symbols.forEach { symbol ->
            val result = collector.collect(symbol, from, to)
            println(
                "%s  요청 %s~%s  |  수신 %d건  저장 %d건  %d페이지  |  실제 %s~%s".format(
                    result.symbol, result.from, result.to,
                    result.fetched, result.saved, result.pages,
                    result.oldest ?: "-", result.newest ?: "-",
                ),
            )
            if (result.isEmpty) {
                log.warn("{} 기간 내 봉이 하나도 없다. 종목코드와 기간을 확인할 것", symbol)
            }
        }
    }

    private fun ApplicationArguments.single(name: String): String? = getOptionValues(name)?.firstOrNull()

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private const val DEFAULT_DAYS = 365L
    }
}
