package dev.teolab.stocklab.market.domain

import java.time.OffsetDateTime

/** 일봉 조회 포트. 구현은 infrastructure. */
interface DailyCandleReader {

    /**
     * @param count 한 번에 받을 봉 개수. 최대 [MAX_COUNT].
     * @param before 이 시각 이하(inclusive)의 봉만. null 이면 최신부터.
     */
    fun read(symbol: String, count: Int = MAX_COUNT, before: OffsetDateTime? = null): CandlePage

    companion object {
        const val MAX_COUNT = 200
    }
}
