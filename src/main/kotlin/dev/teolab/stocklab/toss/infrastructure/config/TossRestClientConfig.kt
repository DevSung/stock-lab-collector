package dev.teolab.stocklab.toss.infrastructure.config

import dev.teolab.stocklab.market.domain.DailyCandleReader
import dev.teolab.stocklab.market.domain.InvestorTradingReader
import dev.teolab.stocklab.market.domain.ShortSellingReader
import dev.teolab.stocklab.toss.application.TossTokenManager
import dev.teolab.stocklab.toss.domain.TokenIssuer
import dev.teolab.stocklab.toss.infrastructure.auth.TossAuthClient
import dev.teolab.stocklab.toss.infrastructure.client.TossCandleClient
import dev.teolab.stocklab.toss.infrastructure.client.TossInvestorTradingClient
import dev.teolab.stocklab.toss.infrastructure.client.TossShortSellingClient
import dev.teolab.stocklab.toss.infrastructure.ratelimit.GroupRateLimiter
import dev.teolab.stocklab.toss.infrastructure.ratelimit.Sleeper
import dev.teolab.stocklab.toss.infrastructure.ratelimit.ThreadSleeper
import dev.teolab.stocklab.toss.infrastructure.retry.RetryPolicy
import dev.teolab.stocklab.toss.infrastructure.retry.TossApiExecutor
import dev.teolab.stocklab.toss.infrastructure.http.BearerTokenInterceptor
import dev.teolab.stocklab.toss.infrastructure.http.TossErrorReader
import dev.teolab.stocklab.toss.infrastructure.http.TossErrorTranslator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.util.DefaultUriBuilderFactory
import java.net.http.HttpClient
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class TossRestClientConfig {

    @Bean
    @ConditionalOnMissingBean
    fun clock(): Clock = Clock.system(KST)

    /**
     * 응답 본문을 여러 번 읽을 수 있게 버퍼링한다.
     * 인터셉터가 `token-revoked` 판정을 위해 본문을 읽어도 statusHandler 가 다시 읽어 requestId 를 남길 수 있다.
     */
    @Bean
    fun tossRequestFactory(properties: TossProperties): ClientHttpRequestFactory {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.http.connectTimeout)
            .build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.http.readTimeout)
        }
        return BufferingClientHttpRequestFactory(factory)
    }

    @Bean
    fun tossErrorTranslator(): TossErrorTranslator = TossErrorTranslator(TossErrorReader())

    /**
     * 토큰 발급 전용. **인터셉터를 붙이지 않는다.** 붙이는 순간 토큰 발급이 다시 토큰을 요구하는 재귀가 된다.
     */
    @Bean
    fun tossAuthRestClient(
        builder: RestClient.Builder,
        properties: TossProperties,
        errorTranslator: TossErrorTranslator,
    ): RestClient = builder.clone()
        .baseUrl(properties.baseUrl)
        .requestFactory(tossRequestFactory(properties))
        .defaultStatusHandler({ it.isError }) { request, response ->
            throw errorTranslator.translate(request, response)
        }
        .build()

    @Bean
    fun tokenIssuer(
        tossAuthRestClient: RestClient,
        properties: TossProperties,
        clock: Clock,
    ): TokenIssuer = TossAuthClient(tossAuthRestClient, properties, clock)

    @Bean
    fun tossTokenManager(
        tokenIssuer: TokenIssuer,
        clock: Clock,
        properties: TossProperties,
    ): TossTokenManager = TossTokenManager(tokenIssuer, clock, properties.auth.expiryMargin)

    /**
     * 데이터 조회용 클라이언트.
     *
     * 인코딩 모드를 VALUES_ONLY 로 고정한다. 기본 모드에서는 쿼리 값의 `+` 가 그대로 남아
     * 서버가 공백으로 디코딩해도 **예외 없이 엉뚱한 기간의 데이터가 들어온다**(2단계 `before` 파라미터).
     * 이 팩토리를 쓰는 대신 모든 동적 값은 URI 변수로만 넘겨야 한다.
     */
    @Bean
    fun tossApiRestClient(
        builder: RestClient.Builder,
        properties: TossProperties,
        tossTokenManager: TossTokenManager,
        errorTranslator: TossErrorTranslator,
    ): RestClient {
        val uriBuilderFactory = DefaultUriBuilderFactory(properties.baseUrl).apply {
            encodingMode = DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY
        }
        return builder.clone()
            .uriBuilderFactory(uriBuilderFactory)
            .requestFactory(tossRequestFactory(properties))
            // BearerTokenInterceptor 는 재실행을 하므로 항상 마지막(가장 안쪽)에 등록한다.
            .requestInterceptor(BearerTokenInterceptor(tossTokenManager))
            .defaultStatusHandler({ it.isError }) { request, response ->
                throw errorTranslator.translate(request, response)
            }
            .build()
    }

    @Bean
    fun sleeper(clock: Clock): Sleeper = ThreadSleeper(clock)

    @Bean
    fun groupRateLimiter(clock: Clock, sleeper: Sleeper): GroupRateLimiter = GroupRateLimiter(clock, sleeper)

    @Bean
    fun tossApiExecutor(
        groupRateLimiter: GroupRateLimiter,
        sleeper: Sleeper,
        clock: Clock,
    ): TossApiExecutor = TossApiExecutor(groupRateLimiter, sleeper, clock, RetryPolicy())

    @Bean
    fun dailyCandleReader(tossApiRestClient: RestClient, tossApiExecutor: TossApiExecutor): DailyCandleReader =
        TossCandleClient(tossApiRestClient, tossApiExecutor)

    @Bean
    fun investorTradingReader(
        tossApiRestClient: RestClient,
        tossApiExecutor: TossApiExecutor,
    ): InvestorTradingReader = TossInvestorTradingClient(tossApiRestClient, tossApiExecutor)

    @Bean
    fun shortSellingReader(
        tossApiRestClient: RestClient,
        tossApiExecutor: TossApiExecutor,
    ): ShortSellingReader = TossShortSellingClient(tossApiRestClient, tossApiExecutor)

    companion object {
        private val KST = java.time.ZoneId.of("Asia/Seoul")
    }
}
