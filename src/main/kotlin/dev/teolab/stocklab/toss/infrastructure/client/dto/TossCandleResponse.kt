package dev.teolab.stocklab.toss.infrastructure.client.dto

import java.math.BigDecimal

/**
 * GET /api/v1/candles 응답. 실측 형태는 아래와 같다.
 *
 * ```
 * { "result": { "candles": [ { "timestamp": "2026-09-17T00:00:00.000+09:00",
 *                              "openPrice": "251500", ... "volume": "11058213",
 *                              "currency": "KRW" } ],
 *               "nextBefore": "2026-09-14T00:00:00.000+09:00" } }
 * ```
 *
 * 주의: 가격과 거래량이 **문자열**로 온다. Jackson 의 문자열→숫자 변환에 기대고 있으므로,
 * 이 계약이 깨지면 TossCandleClientTest 가 먼저 실패한다.
 */
data class TossCandleEnvelope(
    val result: TossCandleResult? = null,
)

data class TossCandleResult(
    val candles: List<TossCandleItem> = emptyList(),
    val nextBefore: String? = null,
)

data class TossCandleItem(
    val timestamp: String,
    val openPrice: BigDecimal,
    val highPrice: BigDecimal,
    val lowPrice: BigDecimal,
    val closePrice: BigDecimal,
    val volume: Long,
    val currency: String? = null,
)
