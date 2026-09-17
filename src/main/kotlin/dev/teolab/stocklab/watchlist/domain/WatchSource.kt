package dev.teolab.stocklab.watchlist.domain

/** 종목이 수집 대상에 들어온 경로. 같은 종목이 여러 출처에 동시에 속할 수 있다. */
enum class WatchSource {
    /** 거래대금 상위 30. 매일 갱신되고 이탈하면 유예 후 정리된다. */
    TOP30,

    /** resources/watchlist 의 yml 로 관리하는 카테고리. yml 이 곧 진실이다. */
    CATEGORY,

    /** 직접 넣은 종목. 어떤 배치도 지우지 않는다. */
    MANUAL,
    ;

    companion object {
        fun from(value: String): WatchSource =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: error("알 수 없는 watchlist 출처: $value")
    }
}
