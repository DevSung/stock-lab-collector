package dev.teolab.stocklab.market.domain

import java.time.OffsetDateTime

/**
 * 일봉 한 페이지.
 *
 * [candles] 는 API 응답 그대로 **최신순**(timestamp 내림차순)이다. 첫 요소가 가장 최근 봉.
 * [nextBefore] 를 다음 요청의 before 로 그대로 넘기면 이어지는 페이지를 받는다. null 이면 마지막 페이지다.
 */
data class CandlePage(
    val candles: List<DailyCandle>,
    val nextBefore: OffsetDateTime?,
) {
    val isLastPage: Boolean get() = nextBefore == null
    val newest: DailyCandle? get() = candles.firstOrNull()
    val oldest: DailyCandle? get() = candles.lastOrNull()

    companion object {
        val EMPTY = CandlePage(emptyList(), null)
    }
}
