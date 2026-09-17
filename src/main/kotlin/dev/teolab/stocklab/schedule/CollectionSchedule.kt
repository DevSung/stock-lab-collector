package dev.teolab.stocklab.schedule

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 배치 스케줄 설정. cron 은 전부 KST 기준이다.
 *
 * [dailyLookbackDays] 가 30 인 이유: 캔들·수급 모두 한 번 요청하면 여러 건을 주므로
 * 1일치를 받든 30일치를 받든 **API 호출 횟수가 같다.** 저장은 덮어쓰기라 중복 부담도 없다.
 * 그래서 넉넉히 받아 두면 종목이 잠시 watchlist 에서 빠졌다 돌아왔을 때 생긴 구멍이 저절로 메워진다.
 */
@ConfigurationProperties(prefix = "stocklab.schedule")
data class CollectionSchedule(
    val enabled: Boolean = false,
    /** 종목 마스터 동기화. 기본: 일요일 05:00 */
    val stockMasterCron: String = "0 0 5 * * SUN",
    /** TOP30 갱신. 기본: 평일 17:30 (장 마감 후) */
    val top30Cron: String = "0 30 17 * * MON-FRI",
    /** 일일 수집. 기본: 평일 18:00 */
    val dailyCollectCron: String = "0 0 18 * * MON-FRI",
    val zone: String = "Asia/Seoul",
    val dailyLookbackDays: Long = 30,
    /** 한 종목 실패가 배치 전체를 멈추지 않게 한다. 이 비율을 넘게 실패하면 배치를 실패로 본다. */
    val failureThreshold: Double = 0.5,
    /** 종목 간 추가 지연. rate limiter 가 이미 페이싱하므로 기본은 0 이다. */
    val perSymbolDelay: Duration = Duration.ZERO,
)
