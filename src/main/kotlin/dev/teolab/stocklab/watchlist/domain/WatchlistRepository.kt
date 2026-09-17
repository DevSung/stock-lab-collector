package dev.teolab.stocklab.watchlist.domain

import java.time.Instant

/** watchlist 저장 포트. */
interface WatchlistRepository {

    /** 있으면 last_seen_at 만 갱신하고, 없으면 새로 넣는다. */
    fun upsertAll(entries: List<WatchEntry>): Int

    /** 실제로 수집할 종목 목록. 여러 출처에 걸쳐 있어도 한 번만 나온다. */
    fun findDistinctSymbols(): List<String>

    fun findBySource(source: WatchSource): List<WatchEntry>

    /** [source] 행 중 [threshold] 이후로 갱신되지 않은 것을 지운다. @return 지운 건수 */
    fun deleteStale(source: WatchSource, threshold: Instant): Int

    /** [source] 행 중 [keep] 에 없는 것을 지운다. yml 에서 빠진 카테고리 종목 정리용. */
    fun deleteMissing(source: WatchSource, keep: Collection<Pair<String, String>>): Int
}
