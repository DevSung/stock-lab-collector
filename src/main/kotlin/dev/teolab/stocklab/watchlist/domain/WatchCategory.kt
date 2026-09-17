package dev.teolab.stocklab.watchlist.domain

/** resources/watchlist 의 yml 한 장을 읽은 결과. */
data class WatchCategory(
    val category: String,
    val description: String?,
    val symbols: List<String>,
) {
    init {
        require(category.isNotBlank()) { "category 가 비어 있다" }
        require(symbols.all { it.isNotBlank() }) { "빈 종목코드가 섞여 있다: $symbols" }
        require(symbols.distinct().size == symbols.size) {
            "$category 에 중복 종목이 있다: ${symbols.groupBy { it }.filterValues { it.size > 1 }.keys}"
        }
    }
}

/** 카테고리 설정 파일을 읽는 포트. */
fun interface WatchCategoryLoader {
    fun loadAll(): List<WatchCategory>
}
