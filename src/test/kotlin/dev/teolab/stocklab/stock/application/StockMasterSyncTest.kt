package dev.teolab.stocklab.stock.application

import dev.teolab.stocklab.stock.domain.ListingStatus
import dev.teolab.stocklab.stock.domain.Market
import dev.teolab.stocklab.stock.domain.Stock
import dev.teolab.stocklab.stock.domain.StockRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StockMasterSyncTest {

    private class FakeRepository : StockRepository {
        val upserted = mutableListOf<Stock>()
        var markDelistedArgs: Pair<Collection<Market>, Collection<String>>? = null
        override fun upsertAll(stocks: List<Stock>): Int { upserted += stocks; return stocks.size }
        override fun markDelisted(markets: Collection<Market>, activeSymbols: Collection<String>): Int {
            markDelistedArgs = markets to activeSymbols
            return 3
        }
        override fun countByStatus(status: ListingStatus): Int = upserted.count { it.listingStatus == status }
    }

    private fun stock(symbol: String, market: Market) =
        Stock(symbol, "종목$symbol", market, market.currency, ListingStatus.ACTIVE, "STOCK", "KR$symbol")

    @Test
    fun `국내 마켓만 동기화한다`() {
        val repository = FakeRepository()
        val requested = mutableListOf<Market>()
        val sync = StockMasterSync({ market -> requested += market; listOf(stock("005930", market)) }, repository)

        sync.sync()

        // 수급 API 가 국내 전용이라 해외 종목을 넣어봐야 unsupported-market 만 난다
        assertEquals(listOf(Market.KOSPI, Market.KOSDAQ), requested)
    }

    @Test
    fun `목록에서 사라진 종목을 폐지로 표시한다`() {
        val repository = FakeRepository()
        val sync = StockMasterSync({ market -> listOf(stock("005930", market)) }, repository)

        val result = sync.sync(listOf(Market.KOSPI))

        assertEquals(3, result.delisted)
        val (markets, active) = requireNotNull(repository.markDelistedArgs)
        assertEquals(listOf(Market.KOSPI), markets.toList())
        assertEquals(listOf("005930"), active.toList())
    }

    @Test
    fun `한 마켓이라도 실패하면 폐지 표시를 건너뛴다`() {
        val repository = FakeRepository()
        val sync = StockMasterSync(
            { market ->
                if (market == Market.KOSDAQ) error("조회 실패") else listOf(stock("005930", market))
            },
            repository,
        )

        val result = sync.sync()

        // 빈 응답과 "정말로 전 종목이 사라짐"을 구분할 수 없다. 잘못 찍으면 멀쩡한 종목이 죽는다.
        assertEquals(0, result.delisted)
        assertNullDelist(repository)
        assertFalse(result.isComplete)
        assertEquals(listOf(Market.KOSDAQ), result.failedMarkets)
    }

    @Test
    fun `실패한 마켓이 있어도 성공한 마켓은 저장한다`() {
        val repository = FakeRepository()
        val sync = StockMasterSync(
            { market -> if (market == Market.KOSDAQ) error("조회 실패") else listOf(stock("005930", market)) },
            repository,
        )

        val result = sync.sync()

        assertEquals(1, result.upserted)
        assertTrue(repository.upserted.any { it.symbol == "005930" })
    }

    private fun assertNullDelist(repository: FakeRepository) =
        assertTrue(repository.markDelistedArgs == null, "폐지 표시를 호출하면 안 된다")
}
