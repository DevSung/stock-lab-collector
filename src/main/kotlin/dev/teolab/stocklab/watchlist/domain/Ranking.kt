package dev.teolab.stocklab.watchlist.domain

import java.math.BigDecimal
import java.time.OffsetDateTime

/** 거래대금 랭킹 한 줄. */
data class RankingEntry(
    val rank: Int,
    val symbol: String,
    val tradingAmount: BigDecimal?,
    val tradingVolume: Long?,
    val lastPrice: BigDecimal?,
    val changeRate: BigDecimal?,
) {
    init {
        require(rank >= 1) { "순위는 1 이상이어야 한다: $rank" }
        require(symbol.isNotBlank()) { "종목코드가 비어 있다" }
    }
}

/** 한 시점의 랭킹 전체. [rankedAt] 은 API 가 알려준 집계 시각이다. */
data class RankingSnapshot(
    val rankedAt: OffsetDateTime,
    val entries: List<RankingEntry>,
) {
    val symbols: List<String> get() = entries.map { it.symbol }
    val isEmpty: Boolean get() = entries.isEmpty()
}

/** 랭킹 조회 포트. */
fun interface RankingReader {
    /** 거래대금 기준 상위 [count] 종목. */
    fun readTopByTradingAmount(count: Int): RankingSnapshot
}

/** TOP30 이력 저장 포트. */
fun interface Top30HistoryStore {
    /** 같은 (거래일, 순위) 가 있으면 덮어쓴다. 장중에 여러 번 돌려도 행이 늘지 않는다. */
    fun save(snapshot: RankingSnapshot): Int
}
