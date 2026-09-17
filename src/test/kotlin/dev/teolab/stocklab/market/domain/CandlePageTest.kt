package dev.teolab.stocklab.market.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CandlePageTest {

    private fun candle(date: LocalDate) = DailyCandle(
        symbol = "005930",
        tradeDate = date,
        open = BigDecimal("100"),
        high = BigDecimal("110"),
        low = BigDecimal("90"),
        close = BigDecimal("105"),
        volume = 1_000L,
        currency = "KRW",
        adjusted = true,
    )

    @Test
    fun `최신순 정렬을 전제로 newest 와 oldest 를 뽑는다`() {
        val page = CandlePage(
            candles = listOf(
                candle(LocalDate.of(2026, 9, 17)),
                candle(LocalDate.of(2026, 9, 16)),
                candle(LocalDate.of(2026, 9, 15)),
            ),
            nextBefore = OffsetDateTime.parse("2026-09-14T00:00:00+09:00"),
        )

        assertEquals(LocalDate.of(2026, 9, 17), page.newest?.tradeDate)
        assertEquals(LocalDate.of(2026, 9, 15), page.oldest?.tradeDate)
        assertFalse(page.isLastPage)
    }

    @Test
    fun `빈 페이지는 마지막 페이지이고 양끝이 없다`() {
        assertTrue(CandlePage.EMPTY.isLastPage)
        assertNull(CandlePage.EMPTY.newest)
        assertNull(CandlePage.EMPTY.oldest)
    }

    @Test
    fun `종목코드가 비었거나 거래량이 음수인 봉은 만들 수 없다`() {
        assertThrows<IllegalArgumentException> { candle(LocalDate.now()).copy(symbol = " ") }
        assertThrows<IllegalArgumentException> { candle(LocalDate.now()).copy(volume = -1L) }
    }
}
