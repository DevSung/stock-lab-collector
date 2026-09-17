package dev.teolab.stocklab.market.application

import dev.teolab.stocklab.market.domain.CandlePage
import dev.teolab.stocklab.market.domain.DailyCandle
import dev.teolab.stocklab.market.domain.DailyCandleReader
import dev.teolab.stocklab.market.domain.DailyCandleStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyCandleCollectorTest {

    private val kst = ZoneId.of("Asia/Seoul")

    /** 저장된 봉을 그대로 들고 있는 가짜 저장소. */
    private class RecordingStore : DailyCandleStore {
        val saved = mutableListOf<DailyCandle>()
        var callCount = 0
        override fun upsertAll(candles: List<DailyCandle>): Int {
            callCount++
            saved += candles
            return candles.size
        }
    }

    /** 미리 만든 페이지를 순서대로 돌려주면서 before 인자를 기록한다. */
    private class ScriptedReader(private val pages: List<CandlePage>) : DailyCandleReader {
        val befores = mutableListOf<OffsetDateTime?>()
        private var index = 0
        override fun read(symbol: String, count: Int, before: OffsetDateTime?): CandlePage {
            befores += before
            return pages.getOrElse(index++) { CandlePage.EMPTY }
        }
    }

    private fun candle(date: LocalDate) = DailyCandle(
        symbol = "005930",
        tradeDate = date,
        open = BigDecimal("100"), high = BigDecimal("110"),
        low = BigDecimal("90"), close = BigDecimal("105"),
        volume = 1_000L, currency = "KRW", adjusted = true,
    )

    /** [newest] 부터 하루씩 거슬러 [size] 개. 실제 응답처럼 최신순이다. */
    private fun page(newest: LocalDate, size: Int, nextBefore: LocalDate?) = CandlePage(
        candles = (0 until size).map { candle(newest.minusDays(it.toLong())) },
        nextBefore = nextBefore?.atStartOfDay(kst)?.toOffsetDateTime(),
    )

    @Test
    fun `nextBefore 를 따라가며 여러 페이지를 모은다`() {
        val reader = ScriptedReader(
            listOf(
                page(LocalDate.of(2026, 9, 17), 3, nextBefore = LocalDate.of(2026, 9, 14)),
                page(LocalDate.of(2026, 9, 14), 3, nextBefore = LocalDate.of(2026, 9, 11)),
                page(LocalDate.of(2026, 9, 11), 3, nextBefore = null),
            ),
        )
        val store = RecordingStore()
        val collector = DailyCandleCollector(reader, store, kst)

        val result = collector.collect("005930", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 17))

        assertEquals(3, result.pages)
        assertEquals(9, result.fetched)
        assertEquals(9, result.saved)
        assertEquals(LocalDate.of(2026, 9, 17), result.newest)
        assertEquals(LocalDate.of(2026, 9, 9), result.oldest)
    }

    @Test
    fun `두 번째 요청부터는 앞 페이지의 nextBefore 를 그대로 넘긴다`() {
        val nextBefore = LocalDate.of(2026, 9, 14)
        val reader = ScriptedReader(
            listOf(
                page(LocalDate.of(2026, 9, 17), 3, nextBefore = nextBefore),
                page(nextBefore, 3, nextBefore = null),
            ),
        )
        val collector = DailyCandleCollector(reader, RecordingStore(), kst)

        collector.collect("005930", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 17))

        assertEquals(2, reader.befores.size)
        // 첫 요청은 to 당일 끝시각(before 는 inclusive 라 9/17 봉이 포함되어야 한다)
        assertEquals(LocalDate.of(2026, 9, 17), reader.befores[0]?.atZoneSameInstant(kst)?.toLocalDate())
        // 두 번째는 응답이 준 nextBefore 그대로. 날짜를 직접 계산하면 휴장일에서 어긋난다.
        assertEquals(nextBefore.atStartOfDay(kst).toOffsetDateTime(), reader.befores[1])
    }

    @Test
    fun `마지막 페이지면 더 요청하지 않는다`() {
        val reader = ScriptedReader(listOf(page(LocalDate.of(2026, 9, 17), 3, nextBefore = null)))
        val collector = DailyCandleCollector(reader, RecordingStore(), kst)

        val result = collector.collect("005930", LocalDate.of(2020, 1, 1), LocalDate.of(2026, 9, 17))

        assertEquals(1, result.pages)
        assertEquals(1, reader.befores.size)
    }

    @Test
    fun `요청 시작일을 지나면 바로 멈춘다`() {
        // 9/17~9/15 페이지를 받았고 from 이 9/16 이면, 더 과거는 볼 필요가 없다
        val reader = ScriptedReader(
            listOf(
                page(LocalDate.of(2026, 9, 17), 3, nextBefore = LocalDate.of(2026, 9, 14)),
                page(LocalDate.of(2026, 9, 14), 3, nextBefore = LocalDate.of(2026, 9, 11)),
            ),
        )
        val store = RecordingStore()
        val collector = DailyCandleCollector(reader, store, kst)

        val result = collector.collect("005930", LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 17))

        assertEquals(1, result.pages)
        assertEquals(2, result.fetched, "9/17 과 9/16 만 저장되어야 한다")
        assertTrue(store.saved.all { it.tradeDate >= LocalDate.of(2026, 9, 16) })
    }

    @Test
    fun `기간 밖의 봉은 저장하지 않는다`() {
        val reader = ScriptedReader(listOf(page(LocalDate.of(2026, 9, 17), 5, nextBefore = null)))
        val store = RecordingStore()
        val collector = DailyCandleCollector(reader, store, kst)

        // 9/17~9/13 을 받았지만 기간은 9/15~9/16
        collector.collect("005930", LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16))

        assertEquals(listOf(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 15)), store.saved.map { it.tradeDate })
    }

    @Test
    fun `빈 응답이면 저장을 시도하지 않는다`() {
        val reader = ScriptedReader(listOf(CandlePage.EMPTY))
        val store = RecordingStore()
        val collector = DailyCandleCollector(reader, store, kst)

        val result = collector.collect("005930", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 17))

        assertTrue(result.isEmpty)
        assertEquals(0, store.callCount)
    }

    @Test
    fun `nextBefore 가 전진하지 않아도 페이지 상한에서 멈춘다`() {
        // 같은 페이지를 무한히 돌려주는 리더 — 무한 루프 방어선 검증
        val stuck = object : DailyCandleReader {
            var calls = 0
            override fun read(symbol: String, count: Int, before: OffsetDateTime?): CandlePage {
                calls++
                return page(LocalDate.of(2026, 9, 17), 3, nextBefore = LocalDate.of(2026, 9, 17))
            }
        }
        val collector = DailyCandleCollector(stuck, RecordingStore(), kst)

        val result = collector.collect("005930", LocalDate.of(1990, 1, 1), LocalDate.of(2026, 9, 17))

        assertEquals(DailyCandleCollector.MAX_PAGES, result.pages)
        assertEquals(DailyCandleCollector.MAX_PAGES, stuck.calls)
    }

    @Test
    fun `from 이 to 보다 뒤면 거부한다`() {
        val collector = DailyCandleCollector(ScriptedReader(emptyList()), RecordingStore(), kst)

        assertThrows<IllegalArgumentException> {
            collector.collect("005930", LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 1))
        }
    }
}
