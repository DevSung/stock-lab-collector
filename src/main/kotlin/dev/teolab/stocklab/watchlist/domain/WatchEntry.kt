package dev.teolab.stocklab.watchlist.domain

import java.time.Instant

/**
 * 수집 대상 한 줄. (종목, 출처, 카테고리) 가 식별자다.
 *
 * [lastSeenAt] 은 이 출처에서 마지막으로 확인된 시각이다.
 * TOP30 은 매일 갱신하면서 이 값을 올리고, 일정 기간 갱신되지 않은 행을 정리한다.
 */
data class WatchEntry(
    val symbol: String,
    val source: WatchSource,
    /** 카테고리명. TOP30/MANUAL 은 빈 문자열이다(PK 컬럼이라 null 을 쓸 수 없다). */
    val category: String = NO_CATEGORY,
    val addedAt: Instant,
    val lastSeenAt: Instant,
) {
    init {
        require(symbol.isNotBlank()) { "종목코드가 비어 있다" }
        require(source == WatchSource.CATEGORY || category == NO_CATEGORY) {
            "$source 출처에는 카테고리를 붙이지 않는다: $category"
        }
        require(source != WatchSource.CATEGORY || category.isNotBlank()) {
            "CATEGORY 출처에는 카테고리명이 있어야 한다"
        }
    }

    companion object {
        const val NO_CATEGORY = ""
    }
}
