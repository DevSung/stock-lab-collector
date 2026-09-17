package dev.teolab.stocklab.bootstrap

import dev.teolab.stocklab.market.domain.DailyCandleReader
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 2단계 검증용. 종목 하나의 일봉을 받아 콘솔에 찍고 끝낸다. 저장은 3단계다.
 *
 *   ./scripts/run.sh --spring.profiles.active=candledemo
 *   ./scripts/run.sh --spring.profiles.active=candledemo --symbol=000660 --count=10
 */
@Component
@Profile("candledemo & !test")  // 테스트 컨텍스트에서는 실행되지 않게 한다
class CandlePrintRunner(
    private val candleReader: DailyCandleReader,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val symbol = args.optionValue("symbol") ?: DEFAULT_SYMBOL
        val count = args.optionValue("count")?.toIntOrNull() ?: DailyCandleReader.MAX_COUNT

        val page = candleReader.read(symbol, count)

        println()
        println("종목 $symbol · 일봉 ${page.candles.size}건")
        println("─".repeat(78))
        println(HEADER)
        println("─".repeat(78))
        page.candles.forEach { candle ->
            println(
                "%-12s %12s %12s %12s %12s %14s".format(
                    candle.tradeDate,
                    candle.open.toPlainString(),
                    candle.high.toPlainString(),
                    candle.low.toPlainString(),
                    candle.close.toPlainString(),
                    "%,d".format(candle.volume),
                ),
            )
        }
        println("─".repeat(78))
        println("기간: ${page.oldest?.tradeDate} ~ ${page.newest?.tradeDate}")
        println("nextBefore: ${page.nextBefore ?: "(없음 — 마지막 페이지)"}")
        println()

        if (page.candles.isEmpty()) {
            log.warn("받은 봉이 없다. 종목코드({})가 맞는지 확인할 것", symbol)
        }
    }

    private fun ApplicationArguments.optionValue(name: String): String? =
        getOptionValues(name)?.firstOrNull()

    companion object {
        private const val DEFAULT_SYMBOL = "005930"
        private const val HEADER = "거래일             시가          고가          저가          종가            거래량"
    }
}
