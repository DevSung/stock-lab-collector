package dev.teolab.stocklab.market.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 일봉 하나. (종목, 거래일) 이 곧 식별자다.
 *
 * [adjusted] 가 true 면 수정주가다. 액면분할 등이 일어나면 **과거 값이 전부 재계산되므로**
 * 같은 (종목, 거래일) 이라도 나중에 받은 값이 다를 수 있다. 적재는 append 가 아니라 덮어쓰기여야 한다.
 */
data class DailyCandle(
    val symbol: String,
    val tradeDate: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long,
    val currency: String?,
    val adjusted: Boolean,
) {
    init {
        require(symbol.isNotBlank()) { "종목코드가 비어 있다" }
        require(volume >= 0) { "거래량이 음수다: $volume" }
    }
}
