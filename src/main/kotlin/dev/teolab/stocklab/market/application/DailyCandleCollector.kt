package dev.teolab.stocklab.market.application

import dev.teolab.stocklab.market.domain.CollectResult
import dev.teolab.stocklab.market.domain.DailyCandleReader
import dev.teolab.stocklab.market.domain.DailyCandleStore
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 일봉을 기간만큼 거슬러 올라가며 수집해 저장한다.
 *
 * 페이징은 API 가 주는 nextBefore 를 그대로 따라간다. 날짜를 직접 계산하지 않는 이유는,
 * 휴장일 때문에 "하루 전"이 다음 봉이라는 보장이 없기 때문이다.
 *
 * 저장은 덮어쓰기라, 같은 기간을 다시 돌리면 그냥 최신 값으로 갱신된다.
 * 액면분할로 수정주가가 재계산됐을 때 이 재수집이 유일한 복구 수단이다.
 */
class DailyCandleCollector(
    private val reader: DailyCandleReader,
    private val store: DailyCandleStore,
    private val zone: ZoneId = KST,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun collect(symbol: String, from: LocalDate, to: LocalDate): CollectResult {
        require(!from.isAfter(to)) { "from($from) 이 to($to) 보다 뒤다" }

        // to 의 그날 마지막 순간부터 거슬러 올라간다. before 는 inclusive 라 to 당일 봉도 포함된다.
        var before: OffsetDateTime? = to.atTime(LocalTime.MAX).atZone(zone).toOffsetDateTime()
        var pages = 0
        var fetched = 0
        var saved = 0
        var oldest: LocalDate? = null
        var newest: LocalDate? = null

        while (pages < MAX_PAGES) {
            val page = reader.read(symbol, DailyCandleReader.MAX_COUNT, before)
            pages++
            if (page.candles.isEmpty()) {
                log.debug("{} {}페이지: 빈 응답 — 중단", symbol, pages)
                break
            }

            val inRange = page.candles.filter { it.tradeDate in from..to }
            if (inRange.isNotEmpty()) {
                saved += store.upsertAll(inRange)
                fetched += inRange.size
                newest = newest ?: inRange.first().tradeDate
                oldest = inRange.last().tradeDate
            }

            // 받은 페이지가 이미 from 보다 과거로 넘어갔으면 더 볼 필요가 없다.
            val reachedStart = page.candles.last().tradeDate <= from
            if (reachedStart || page.isLastPage) {
                log.debug(
                    "{} 수집 종료 ({}페이지, {}). 이유: {}",
                    symbol, pages, oldest,
                    if (reachedStart) "요청 시작일 도달" else "마지막 페이지",
                )
                break
            }
            before = page.nextBefore
        }

        if (pages >= MAX_PAGES) {
            log.warn("{} 페이지 상한({})에 걸려 중단했다. 기간을 나눠서 다시 돌릴 것", symbol, MAX_PAGES)
        }

        return CollectResult(symbol, from, to, fetched, saved, pages, oldest, newest)
            .also { log.info("{} 수집 완료: {}건 저장 ({} ~ {}), {}페이지", symbol, it.saved, it.oldest, it.newest, it.pages) }
    }

    /** 최근 [days] 일치만 수집한다. 일일 배치용. */
    fun collectRecent(symbol: String, days: Long, today: LocalDate): CollectResult =
        collect(symbol, today.minusDays(days), today)

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")

        /**
         * 무한 루프 방어선. 페이지당 최대 200봉이므로 200페이지면 약 160년치다.
         * 여기 걸렸다면 nextBefore 가 전진하지 않는 상황을 의심해야 한다.
         */
        const val MAX_PAGES = 200
    }
}
