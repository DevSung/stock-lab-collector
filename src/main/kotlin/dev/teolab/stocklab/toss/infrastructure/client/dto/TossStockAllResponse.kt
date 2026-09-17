package dev.teolab.stocklab.toss.infrastructure.client.dto

/**
 * GET /api/v1/stocks/all?market=KOSPI 응답. 실측 형태:
 *
 * ```
 * { "result": [ { "symbol": "000020", "name": "동화약품",
 *                 "securityType": "STOCK", "isCommonShare": true,
 *                 "isinCode": "KR7000020008" } ] }
 * ```
 *
 * result 가 객체가 아니라 **배열**이다(캔들·랭킹과 다르다). KOSPI 기준 2481건이 한 번에 온다.
 * 통화나 상장상태 필드는 없다. 통화는 market 으로 정하고, 상장상태는 목록 포함 여부로 판정한다.
 */
data class TossStockAllEnvelope(
    val result: List<TossStockAllItem> = emptyList(),
)

data class TossStockAllItem(
    val symbol: String,
    val name: String,
    val securityType: String? = null,
    val isCommonShare: Boolean? = null,
    val isinCode: String? = null,
)
