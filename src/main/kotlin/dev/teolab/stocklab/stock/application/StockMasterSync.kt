package dev.teolab.stocklab.stock.application

import dev.teolab.stocklab.stock.domain.Market
import dev.teolab.stocklab.stock.domain.StockMasterReader
import dev.teolab.stocklab.stock.domain.StockRepository
import org.slf4j.LoggerFactory

/**
 * 종목 마스터를 동기화한다. 주 1회면 충분하다.
 *
 * 상장폐지는 별도 API 가 없다. **목록에서 사라진 종목을 폐지로 표시**하는 방식으로 잡는다.
 * 그래서 마켓 하나라도 조회에 실패하면 폐지 표시를 건너뛴다. 응답이 비어 있는 것과
 * 진짜로 전 종목이 사라진 것을 구분할 수 없기 때문이다.
 */
class StockMasterSync(
    private val reader: StockMasterReader,
    private val repository: StockRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sync(markets: List<Market> = Market.DOMESTIC): SyncResult {
        val collected = mutableListOf<dev.teolab.stocklab.stock.domain.Stock>()
        val failed = mutableListOf<Market>()

        markets.forEach { market ->
            runCatching { reader.readAll(market) }
                .onSuccess { collected += it }
                .onFailure {
                    failed += market
                    log.error("{} 종목 목록 조회 실패: {}", market, it.message)
                }
        }

        val upserted = repository.upsertAll(collected)
        val delisted = if (failed.isEmpty()) {
            repository.markDelisted(markets, collected.map { it.symbol })
        } else {
            log.warn("{} 조회에 실패해 상장폐지 표시를 건너뛴다", failed)
            0
        }

        return SyncResult(collected.size, upserted, delisted, failed)
            .also { log.info("종목 마스터 동기화: {}건 반영, 폐지 표시 {}건", it.upserted, it.delisted) }
    }

    data class SyncResult(
        val fetched: Int,
        val upserted: Int,
        val delisted: Int,
        val failedMarkets: List<Market>,
    ) {
        val isComplete: Boolean get() = failedMarkets.isEmpty()
    }
}
