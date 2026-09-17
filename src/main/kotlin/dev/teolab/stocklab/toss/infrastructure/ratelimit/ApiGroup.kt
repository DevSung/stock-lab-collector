package dev.teolab.stocklab.toss.infrastructure.ratelimit

/**
 * 토스가 정한 API 그룹. 한도는 클라이언트 × 그룹 단위 초당 호출 수다.
 * 그룹마다 독립적인 카운터를 쓰므로 rate limiter 도 그룹별로 따로 둔다.
 */
enum class ApiGroup(val documentedLimitPerSecond: Int) {
    /** GET /api/v1/candles */
    MARKET_DATA_CHART(20),

    /** 수급: investor-trading, short-selling */
    STOCK_TRADING_TREND(10),

    /** 종목 정보, 랭킹 */
    STOCK(5),

    /** GET /api/v1/stocks/all */
    STOCK_ALL(1),
}
