package dev.teolab.stocklab.watchlist.application

import dev.teolab.stocklab.watchlist.domain.WatchCategory
import dev.teolab.stocklab.watchlist.domain.WatchCategoryLoader
import dev.teolab.stocklab.watchlist.domain.WatchEntry
import dev.teolab.stocklab.watchlist.domain.WatchSource
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryWatchlistSyncTest {

    private val now = Instant.parse("2026-09-17T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private class FakeRepository : WatchlistRepository {
        val upserted = mutableListOf<WatchEntry>()
        var deleteMissingArgs: Pair<WatchSource, Collection<Pair<String, String>>>? = null
        override fun upsertAll(entries: List<WatchEntry>): Int { upserted += entries; return entries.size }
        override fun findDistinctSymbols(): List<String> = upserted.map { it.symbol }.distinct()
        override fun findBySource(source: WatchSource): List<WatchEntry> = upserted.filter { it.source == source }
        override fun deleteStale(source: WatchSource, threshold: Instant): Int = 0
        override fun deleteMissing(source: WatchSource, keep: Collection<Pair<String, String>>): Int {
            deleteMissingArgs = source to keep
            return 1
        }
    }

    private fun sync(repository: WatchlistRepository, vararg categories: WatchCategory) =
        CategoryWatchlistSync(WatchCategoryLoader { categories.toList() }, repository, clock)

    @Test
    fun `카테고리별로 종목을 CATEGORY 출처로 넣는다`() {
        val repository = FakeRepository()

        val result = sync(
            repository,
            WatchCategory("semiconductor", "반도체", listOf("005930", "000660")),
            WatchCategory("battery", "2차전지", listOf("373220")),
        ).sync()

        assertEquals(2, result.categories)
        assertEquals(3, result.symbols)
        assertTrue(repository.upserted.all { it.source == WatchSource.CATEGORY })
        assertEquals(
            listOf("semiconductor", "semiconductor", "battery"),
            repository.upserted.map { it.category },
        )
    }

    @Test
    fun `같은 종목이 두 카테고리에 있으면 각각 한 줄씩 들어간다`() {
        val repository = FakeRepository()

        sync(
            repository,
            WatchCategory("semiconductor", null, listOf("005930")),
            WatchCategory("largecap", null, listOf("005930")),
        ).sync()

        assertEquals(2, repository.upserted.size)
        assertEquals(setOf("semiconductor", "largecap"), repository.upserted.map { it.category }.toSet())
    }

    @Test
    fun `yml 에서 빠진 조합은 CATEGORY 출처에서만 지운다`() {
        val repository = FakeRepository()

        sync(repository, WatchCategory("semiconductor", null, listOf("005930"))).sync()

        val (source, keep) = requireNotNull(repository.deleteMissingArgs)
        // TOP30/MANUAL 로 들어온 같은 종목까지 지워버리면 안 된다
        assertEquals(WatchSource.CATEGORY, source)
        assertEquals(listOf("005930" to "semiconductor"), keep.toList())
    }

    @Test
    fun `yml 이 하나도 없으면 CATEGORY 행을 전부 정리한다`() {
        val repository = FakeRepository()

        val result = sync(repository).sync()

        assertEquals(0, result.symbols)
        assertEquals(emptyList(), requireNotNull(repository.deleteMissingArgs).second.toList())
    }
}
