package dev.teolab.stocklab.market.domain

import java.time.LocalDate

/** 한 종목의 일봉 수집 결과. */
data class CollectResult(
    val symbol: String,
    val from: LocalDate,
    val to: LocalDate,
    /** API 에서 받아 기간 조건을 통과한 봉 수 */
    val fetched: Int,
    /** DB 에 삽입되거나 갱신된 건수 */
    val saved: Int,
    /** 호출한 페이지 수 */
    val pages: Int,
    val oldest: LocalDate?,
    val newest: LocalDate?,
) {
    val isEmpty: Boolean get() = fetched == 0
}
