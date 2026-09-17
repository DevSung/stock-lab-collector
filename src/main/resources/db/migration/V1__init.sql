-- 수집 대상 테이블 6개.
--
-- 핵심 제약: 일봉·수급은 (종목, 거래일) 이 곧 식별자다.
-- 수정주가는 액면분할 등이 생기면 과거 값이 전부 재계산되므로 append-only 가 아니라
-- 덮어쓰기(UPSERT)를 전제로 설계했다. 그래서 PK 를 복합키로 잡고 collected_at 으로 마지막 수집 시각을 남긴다.

CREATE TABLE stock (
    symbol         VARCHAR(20)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    market         VARCHAR(20)  NOT NULL,
    currency       CHAR(3)      NOT NULL,
    listing_status VARCHAR(20)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (symbol)
) COMMENT = '종목 마스터';

CREATE TABLE watchlist (
    symbol   VARCHAR(20) NOT NULL,
    source   VARCHAR(20) NOT NULL COMMENT 'TOP30 / CATEGORY / MANUAL',
    category VARCHAR(50) NULL     COMMENT '설정 파일(resources/watchlist/*.yml)의 카테고리명',
    added_at DATETIME(6) NOT NULL,
    PRIMARY KEY (symbol)
) COMMENT = '수집 대상 종목. 전체 종목이 아니라 여기 있는 것만 모은다';

CREATE TABLE daily_candle (
    symbol       VARCHAR(20)    NOT NULL,
    trade_date   DATE           NOT NULL,
    open_price   DECIMAL(20, 4) NOT NULL,
    high_price   DECIMAL(20, 4) NOT NULL,
    low_price    DECIMAL(20, 4) NOT NULL,
    close_price  DECIMAL(20, 4) NOT NULL,
    volume       BIGINT         NOT NULL,
    currency     CHAR(3)        NULL,
    adjusted     BOOLEAN        NOT NULL COMMENT '수정주가 여부. true 면 과거 값이 재계산될 수 있다',
    collected_at DATETIME(6)    NOT NULL,
    PRIMARY KEY (symbol, trade_date),
    KEY idx_daily_candle_trade_date (trade_date)
) COMMENT = '일봉';

CREATE TABLE investor_trading (
    symbol                 VARCHAR(20) NOT NULL,
    trade_date             DATE        NOT NULL,
    individual_net         BIGINT      NULL COMMENT '개인 순매수 수량',
    foreign_net            BIGINT      NULL COMMENT '외국인 순매수 수량',
    institution_net        BIGINT      NULL COMMENT '기관 순매수 수량',
    other_corporation_net  BIGINT      NULL COMMENT '기타법인 순매수 수량',
    collected_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (symbol, trade_date),
    KEY idx_investor_trading_trade_date (trade_date)
) COMMENT = '투자자별 매매동향 (국내 종목만)';

CREATE TABLE short_selling (
    symbol       VARCHAR(20)    NOT NULL,
    trade_date   DATE           NOT NULL,
    short_volume BIGINT         NULL COMMENT '공매도 거래량',
    short_value  DECIMAL(24, 4) NULL COMMENT '공매도 거래대금',
    short_ratio  DECIMAL(9, 4)  NULL COMMENT '공매도 비중(%)',
    collected_at DATETIME(6)    NOT NULL,
    PRIMARY KEY (symbol, trade_date),
    KEY idx_short_selling_trade_date (trade_date)
) COMMENT = '공매도 동향 (국내 종목만)';

CREATE TABLE collection_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    job_name     VARCHAR(50)  NOT NULL,
    symbol       VARCHAR(20)  NULL,
    target_range VARCHAR(50)  NULL COMMENT '수집 대상 기간 (예: 2024-01-01~2026-09-17)',
    status       VARCHAR(20)  NOT NULL COMMENT 'RUNNING / SUCCESS / FAILED',
    message      TEXT         NULL,
    started_at   DATETIME(6)  NOT NULL,
    finished_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_collection_log_job (job_name, started_at)
) COMMENT = '수집 작업 이력';
