package dev.teolab.stocklab.toss.infrastructure.client.dto

import java.math.BigDecimal

/**
 * GET /api/v1/rankings 응답. 실측 형태:
 *
 * ```
 * { "result": { "rankedAt": "2026-09-17T14:22:21.305+09:00",
 *               "rankings": [ { "rank": 1, "symbol": "000660", "currency": "KRW",
 *                               "price": { "lastPrice": "1756000", "basePrice": "1759000",
 *                                          "changeRate": "-0.0017" },
 *                               "tradingVolume": "3055099",
 *                               "tradingAmount": "5364181931000" } ] } }
 * ```
 */
data class TossRankingEnvelope(
    val result: TossRankingResult? = null,
)

data class TossRankingResult(
    val rankedAt: String? = null,
    val rankings: List<TossRankingItem> = emptyList(),
)

data class TossRankingItem(
    val rank: Int,
    val symbol: String,
    val currency: String? = null,
    val price: TossRankingPrice? = null,
    val tradingVolume: Long? = null,
    val tradingAmount: BigDecimal? = null,
)

data class TossRankingPrice(
    val lastPrice: BigDecimal? = null,
    val basePrice: BigDecimal? = null,
    val changeRate: BigDecimal? = null,
)
