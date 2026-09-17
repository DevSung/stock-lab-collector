package dev.teolab.stocklab.market.domain

import java.math.BigDecimal
import java.time.LocalDate

/** (종목, 거래일) 로 식별되는 수급 레코드. 페이지 순회를 한 벌로 처리하기 위한 공통 타입. */
interface DatedRecord {
    val symbol: String
    val tradeDate: LocalDate
}

/**
 * 투자자별 매매동향. 국내 종목만 제공된다.
 *
 * 당일 데이터는 장 마감 전이면 일부 주체가 null 로 온다(실측: 당일 individual, otherCorporation 이 null).
 * 확정되면 다음 수집에서 덮어써지므로 null 을 그대로 저장한다.
 */
data class InvestorTrading(
    override val symbol: String,
    override val tradeDate: LocalDate,
    val individualNet: Long?,
    val foreignerNet: Long?,
    val institutionNet: Long?,
    val otherCorporationNet: Long?,
) : DatedRecord {
    init {
        require(symbol.isNotBlank()) { "종목코드가 비어 있다" }
    }
}

/**
 * 공매도 동향. 국내 종목만 제공된다.
 *
 * 공시가 하루 늦다(실측: 일봉은 9/17 까지 있는데 공매도는 9/16 이 최신).
 * 그래서 최근 하루는 비어 있는 게 정상이다.
 */
data class ShortSelling(
    override val symbol: String,
    override val tradeDate: LocalDate,
    val volume: Long?,
    val amount: BigDecimal?,
    val volumeRate: BigDecimal?,
    val amountRate: BigDecimal?,
) : DatedRecord {
    init {
        require(symbol.isNotBlank()) { "종목코드가 비어 있다" }
    }
}

/**
 * 수급 API 한 페이지.
 *
 * 캔들과 페이징 방식이 다르다. 캔들은 `before`(ISO 일시)를 쓰는데 수급은 `until`(날짜)이고,
 * 한 번에 받을 수 있는 최대 건수도 200 이 아니라 100 이다.
 */
data class TrendPage<T : DatedRecord>(
    val records: List<T>,
    val nextUntil: LocalDate?,
) {
    val isLastPage: Boolean get() = nextUntil == null

    companion object {
        const val MAX_COUNT = 100
        fun <T : DatedRecord> empty(): TrendPage<T> = TrendPage(emptyList(), null)
    }
}

/** 투자자별 매매동향 조회 포트. */
fun interface InvestorTradingReader {
    fun read(symbol: String, count: Int, until: LocalDate?): TrendPage<InvestorTrading>
}

/** 공매도 동향 조회 포트. */
fun interface ShortSellingReader {
    fun read(symbol: String, count: Int, until: LocalDate?): TrendPage<ShortSelling>
}

/** 수급 저장 포트. 일봉과 마찬가지로 덮어쓰기다. */
fun interface InvestorTradingStore {
    fun upsertAll(records: List<InvestorTrading>): Int
}

fun interface ShortSellingStore {
    fun upsertAll(records: List<ShortSelling>): Int
}
