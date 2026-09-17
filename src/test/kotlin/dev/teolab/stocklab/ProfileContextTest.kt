package dev.teolab.stocklab

import dev.teolab.stocklab.market.application.DailyCandleCollector
import dev.teolab.stocklab.market.application.TradingTrendCollector
import dev.teolab.stocklab.market.domain.DailyCandleReader
import dev.teolab.stocklab.schedule.DailyCollectionJob
import dev.teolab.stocklab.toss.application.TossTokenManager
import dev.teolab.stocklab.watchlist.domain.WatchlistRepository
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 프로파일마다 컨텍스트가 실제로 뜨는지 확인한다.
 *
 * tokencheck / candledemo 는 DataSource 를 제외하므로, DB 가 필요한 빈이 무조건 등록되면
 * **그 프로파일만 조용히 깨진다.** 다른 테스트로는 잡히지 않아서 여기서 막는다.
 *
 * ApplicationRunner 는 @SpringBootTest 에서도 **실행된다.** 그래서 bootstrap 의 런너들은
 * `@Profile("<name> & !test")` 로 막아두고, 여기서는 test 프로파일을 함께 켠다.
 * 이게 없으면 컨텍스트 로딩만으로 실제 수집이 돌아간다.
 */
class ProfileContextTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("tokencheck", "test")
    inner class TokenCheckProfile {

        @Autowired
        private lateinit var context: ApplicationContext

        @Test
        fun `DB 없이도 토큰 경로만으로 기동된다`() {
            assertNotNull(context.getBean(TossTokenManager::class.java))
            assertTrue(
                context.getBeanNamesForType(DailyCollectionJob::class.java).isEmpty(),
                "DB 가 필요한 빈이 등록되면 이 프로파일은 기동에 실패한다",
            )
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("candledemo", "test")
    inner class CandleDemoProfile {

        @Autowired
        private lateinit var context: ApplicationContext

        @Test
        fun `DB 없이 일봉 조회 경로까지 기동된다`() {
            assertNotNull(context.getBean(DailyCandleReader::class.java))
            assertTrue(
                context.getBeanNamesForType(DailyCandleCollector::class.java).isEmpty(),
                "적재용 빈은 DataSource 가 없으므로 등록되면 안 된다",
            )
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("job", "test")
    inner class JobProfile {

        @Autowired
        private lateinit var context: ApplicationContext

        @Test
        fun `배치 프로파일은 수집에 필요한 빈이 모두 뜬다`() {
            assertNotNull(context.getBean(DailyCollectionJob::class.java))
            assertNotNull(context.getBean(DailyCandleCollector::class.java))
            assertNotNull(context.getBean(TradingTrendCollector::class.java))
            assertNotNull(context.getBean(WatchlistRepository::class.java))
        }
    }

    @Nested
    // 상주 프로파일은 eager-init 가 true 라 인라인 속성으로 끈다(테스트가 실제 토큰을 태우면 안 된다)
    @SpringBootTest(properties = ["toss.auth.eager-init=false"])
    @ActiveProfiles("scheduler", "test")
    inner class SchedulerProfile {

        @Autowired
        private lateinit var context: ApplicationContext

        @Test
        fun `스케줄러 프로파일은 스케줄 등록까지 뜬다`() {
            assertNotNull(context.getBean(DailyCollectionJob::class.java))
            assertTrue(
                context.containsBean("collectionScheduler"),
                "stocklab.schedule.enabled=true 인데 스케줄러가 등록되지 않았다",
            )
        }
    }
}
