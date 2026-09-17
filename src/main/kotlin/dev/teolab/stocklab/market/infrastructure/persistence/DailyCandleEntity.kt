package dev.teolab.stocklab.market.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * 일봉 테이블 매핑.
 *
 * 실제 적재는 성능 때문에 [DailyCandleJdbcStore] 의 배치 UPSERT 가 담당한다.
 * 이 엔티티의 역할은 **스키마 계약을 코드로 고정하는 것**이다.
 * `ddl-auto: validate` 가 기동 시 테이블과 대조해, 마이그레이션과 코드가 어긋나면 바로 실패시킨다.
 */
@Entity
@Table(name = "daily_candle")
class DailyCandleEntity(
    @EmbeddedId
    var id: DailyCandleEntityId,

    @Column(name = "open_price", nullable = false, precision = 20, scale = 4)
    var openPrice: BigDecimal,

    @Column(name = "high_price", nullable = false, precision = 20, scale = 4)
    var highPrice: BigDecimal,

    @Column(name = "low_price", nullable = false, precision = 20, scale = 4)
    var lowPrice: BigDecimal,

    @Column(name = "close_price", nullable = false, precision = 20, scale = 4)
    var closePrice: BigDecimal,

    @Column(name = "volume", nullable = false)
    var volume: Long,

    @Column(name = "currency", length = 3)
    var currency: String?,

    @Column(name = "adjusted", nullable = false)
    var adjusted: Boolean,

    @Column(name = "collected_at", nullable = false)
    var collectedAt: Instant,
)

@Embeddable
class DailyCandleEntityId(
    @Column(name = "symbol", nullable = false, length = 20)
    var symbol: String,

    @Column(name = "trade_date", nullable = false)
    var tradeDate: LocalDate,
) : Serializable {
    override fun equals(other: Any?): Boolean =
        this === other || (other is DailyCandleEntityId && symbol == other.symbol && tradeDate == other.tradeDate)

    override fun hashCode(): Int = 31 * symbol.hashCode() + tradeDate.hashCode()
}
