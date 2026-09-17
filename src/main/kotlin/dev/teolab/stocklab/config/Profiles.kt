package dev.teolab.stocklab.config

/**
 * 프로파일 표현식 상수.
 *
 * tokencheck / candledemo 는 DataSource·JPA·Flyway 자동설정을 제외한다.
 * MySQL 이 꺼져 있어도 토큰 발급이나 API 응답만 따로 확인할 수 있게 하려는 것이다(특히 최초 설정 때).
 * 그래서 JdbcTemplate 이 필요한 설정 클래스는 이 프로파일에서 빠져야 한다.
 *
 * `@ConditionalOnBean(JdbcTemplate::class)` 은 쓸 수 없다.
 * 자동설정이 사용자 설정보다 **나중에** 처리되므로, DataSource 가 멀쩡히 있어도 false 로 평가된다.
 */
object Profiles {
    /** JdbcTemplate 이 있는 환경에서만 활성화한다. */
    const val REQUIRES_DATABASE = "!tokencheck & !candledemo"
}
