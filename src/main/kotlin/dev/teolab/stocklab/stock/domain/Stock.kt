package dev.teolab.stocklab.stock.domain

/** 종목 마스터 한 줄. */
data class Stock(
    val symbol: String,
    val name: String,
    val market: Market,
    val currency: String,
    val listingStatus: ListingStatus,
    val securityType: String?,
    val isinCode: String?,
) {
    init {
        require(symbol.isNotBlank()) { "종목코드가 비어 있다" }
        require(name.isNotBlank()) { "종목명이 비어 있다" }
    }
}

/**
 * 토스가 허용하는 market 값. API 가 allowedValues 로 알려준 그대로다.
 *
 * 이 프로젝트는 국내만 수집하므로 [KOSPI], [KOSDAQ] 만 동기화한다.
 * 수급 API 는 국내 종목 전용이라 해외 종목을 넣으면 unsupported-market 으로 떨어진다.
 */
enum class Market(val currency: String, val domestic: Boolean) {
    KOSPI("KRW", true),
    KOSDAQ("KRW", true),
    KR_ETC("KRW", true),
    NYSE("USD", false),
    NASDAQ("USD", false),
    AMEX("USD", false),
    US_ETC("USD", false),
    ;

    companion object {
        val DOMESTIC: List<Market> = entries.filter { it.domestic && it != KR_ETC }
    }
}

/**
 * 상장 상태.
 *
 * stocks/all 응답에는 상태 필드가 없다. 대신 **목록에 있으면 상장, 사라졌으면 폐지**로 판정한다.
 * 종목별로 다시 조회하지 않고도 상장폐지를 잡아낼 수 있다.
 */
enum class ListingStatus {
    ACTIVE,
    DELISTED,
}

/** 종목 마스터 조회 포트. */
fun interface StockMasterReader {
    fun readAll(market: Market): List<Stock>
}

/** 종목 마스터 저장 포트. */
interface StockRepository {
    fun upsertAll(stocks: List<Stock>): Int

    /** [markets] 안에 있는 종목 중 [activeSymbols] 에 없는 것을 폐지로 표시한다. @return 표시한 건수 */
    fun markDelisted(markets: Collection<Market>, activeSymbols: Collection<String>): Int

    fun countByStatus(status: ListingStatus): Int
}
