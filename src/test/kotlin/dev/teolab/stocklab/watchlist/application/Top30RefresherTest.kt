package dev.teolab.stocklab.watchlist.application

import dev.teolab.stocklab.watchlist.domain.RankingEntry
import dev.teolab.stocklab.watchlist.domain.RankingReader
import dev.teolab.stocklab.watchlist.domain.RankingSnapshot
import dev.teolab.stocklab.watchlist.domain.Top30HistoryStore
import dev.teolab.stocklab.watchlist.domain.WatchEntry
import dev.teolab.stocklab.watchlist.domain.WatchSource
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Top30RefresherTest {

    private val now = Instant.parse("2026-09-17T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val grace = Duration.ofDays(30)

    private class FakeRepository : WatchlistRepository {
        val upserted = mutableListOf<WatchEntry>()
        var deleteStaleCalls = mutableListOf<Pair<WatchSource, Instant>>()
        override fun upsertAll(entries: List<WatchEntry>): Int { upserted += entries; return entries.size }
        override fun findDistinctSymbols(): List<String> = upserted.map { it.symbol }.distinct()
        override fun findBySource(source: WatchSource): List<WatchEntry> = upserted.filter { it.source == source }
        override fun deleteStale(source: WatchSource, threshold: Instant): Int {
            deleteStaleCalls += source to threshold
            return 2
        }
        override fun deleteMissing(source: WatchSource, keep: Collection<Pair<String, String>>): Int = 0
    }

    private class FakeHistoryStore : Top30HistoryStore {
        var saved: RankingSnapshot? = null
        override fun save(snapshot: RankingSnapshot): Int { saved = snapshot; return snapshot.entries.size }
    }

    private fun snapshot(vararg symbols: String) = RankingSnapshot(
        rankedAt = OffsetDateTime.parse("2026-09-17T18:00:00+09:00"),
        entries = symbols.mapIndexed { i, s ->
            RankingEntry(i + 1, s, BigDecimal("1000"), 100L, BigDecimal("50000"), BigDecimal("0.01"))
        },
    )

    private fun refresher(
        snapshot: RankingSnapshot,
        repository: WatchlistRepository,
        history: Top30HistoryStore = FakeHistoryStore(),
    ) = Top30Refresher(
        rankingReader = RankingReader { snapshot },
        watchlistRepository = repository,
        historyStore = history,
        clock = clock,
        gracePeriod = grace,
    )

    @Test
    fun `랭킹 종목을 TOP30 출처로 watchlist 에 반영한다`() {
        val repository = FakeRepository()

        val result = refresher(snapshot("000660", "005930"), repository).refresh()

        assertEquals(2, result.refreshed)
        assertEquals(listOf("000660", "005930"), repository.upserted.map { it.symbol })
        assertTrue(repository.upserted.all { it.source == WatchSource.TOP30 })
        assertTrue(repository.upserted.all { it.category == WatchEntry.NO_CATEGORY })
    }

    @Test
    fun `이탈 종목은 즉시 지우지 않고 유예 기간이 지난 것만 정리한다`() {
        val repository = FakeRepository()

        refresher(snapshot("000660"), repository).refresh()

        // 오늘 랭킹에 없다고 바로 지우면 재진입까지의 기간이 데이터 구멍이 된다.
        // 30일 전보다 오래된 행만 정리 대상이다.
        assertEquals(1, repository.deleteStaleCalls.size)
        val (source, threshold) = repository.deleteStaleCalls.single()
        assertEquals(WatchSource.TOP30, source)
        assertEquals(now.minus(grace), threshold)
    }

    @Test
    fun `순위 이력을 스냅샷 그대로 남긴다`() {
        val history = FakeHistoryStore()
        val snapshot = snapshot("000660", "005930", "069500")

        val result = refresher(snapshot, FakeRepository(), history).refresh()

        assertEquals(3, result.historyRows)
        assertEquals(listOf(1, 2, 3), history.saved?.entries?.map { it.rank })
        assertEquals(snapshot.rankedAt, history.saved?.rankedAt)
    }

    @Test
    fun `랭킹이 비면 watchlist 를 건드리지 않는다`() {
        val repository = FakeRepository()
        val empty = RankingSnapshot(OffsetDateTime.parse("2026-09-17T18:00:00+09:00"), emptyList())

        val result = refresher(empty, repository).refresh()

        assertEquals(0, result.refreshed)
        assertTrue(repository.upserted.isEmpty())
        // 빈 응답을 근거로 기존 종목을 지워버리면 안 된다
        assertTrue(repository.deleteStaleCalls.isEmpty())
    }
}
