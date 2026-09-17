package dev.teolab.stocklab.toss.infrastructure.client.dto

import java.math.BigDecimal

/**
 * GET /api/v1/stocks/{symbol}/investor-trading 응답. 실측 형태:
 *
 * ```
 * { "result": { "nextUntil": "2026-09-03",
 *               "records": [ { "date": "2026-09-17", "updatedAt": "...",
 *                              "individual": null,
 *                              "foreigner": { "buyVolume": "1018127", "sellVolume": "1736298",
 *                                             "netBuyVolume": "-718171" },
 *                              "institution": { ..., "breakdown": { ... } },
 *                              "otherCorporation": null,
 *                              "foreignerHolding": { ... }, "cfd": null } ] } }
 * ```
 *
 * 당일 레코드는 장 마감 전이면 일부 주체가 null 이다. 확정되면 다음 수집에서 덮어써진다.
 */
data class TossInvestorTradingEnvelope(
    val result: TossInvestorTradingResult? = null,
)

data class TossInvestorTradingResult(
    val nextUntil: String? = null,
    val records: List<TossInvestorTradingRecord> = emptyList(),
)

data class TossInvestorTradingRecord(
    val date: String,
    val individual: TossTradingVolume? = null,
    val foreigner: TossTradingVolume? = null,
    val institution: TossTradingVolume? = null,
    val otherCorporation: TossTradingVolume? = null,
)

data class TossTradingVolume(
    val buyVolume: Long? = null,
    val sellVolume: Long? = null,
    val netBuyVolume: Long? = null,
)

/**
 * GET /api/v1/stocks/{symbol}/short-selling 응답. 실측 형태:
 *
 * ```
 * { "result": { "nextUntil": "2026-09-02",
 *               "records": [ { "date": "2026-09-16", "updatedAt": "...",
 *                              "shortSellingVolume": "615534",
 *                              "shortSellingAmount": "154876820750",
 *                              "shortSellingVolumeRate": "0.05235",
 *                              "shortSellingAmountRate": "0.0524" } ] } }
 * ```
 */
data class TossShortSellingEnvelope(
    val result: TossShortSellingResult? = null,
)

data class TossShortSellingResult(
    val nextUntil: String? = null,
    val records: List<TossShortSellingRecord> = emptyList(),
)

data class TossShortSellingRecord(
    val date: String,
    val shortSellingVolume: Long? = null,
    val shortSellingAmount: BigDecimal? = null,
    val shortSellingVolumeRate: BigDecimal? = null,
    val shortSellingAmountRate: BigDecimal? = null,
)
