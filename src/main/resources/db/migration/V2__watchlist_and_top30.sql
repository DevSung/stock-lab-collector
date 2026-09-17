-- watchlist 재설계.
--
-- V1 의 PRIMARY KEY(symbol) 로는 한 종목이 하나의 출처에만 속할 수 있었다.
-- 그래서 삼성전자가 semiconductor.yml 에도 있고 오늘 거래대금 TOP30 에도 들면
-- TOP30 갱신이 카테고리 행을 덮어쓰고, 다음날 TOP30 에서 빠질 때 카테고리 종목까지 함께 사라진다.
-- 출처(및 카테고리)별로 행을 나눠 공존시킨다. 수집 대상은 DISTINCT symbol 로 뽑는다.
ALTER TABLE watchlist
    DROP PRIMARY KEY,
    MODIFY COLUMN category VARCHAR(50) NOT NULL DEFAULT ''
        COMMENT '카테고리명. TOP30/MANUAL 은 빈 문자열(PK 컬럼이라 NULL 불가)',
    ADD COLUMN last_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '이 출처에서 마지막으로 확인된 시각. TOP30 이탈 유예 판단에 쓴다',
    ADD PRIMARY KEY (symbol, source, category);

-- 거래대금 TOP30 일별 스냅샷.
-- watchlist 는 "지금 수집할 종목"만 담으므로 과거 순위가 남지 않는다.
-- 순위 이력 자체가 분석 소재라 따로 쌓는다.
CREATE TABLE top30_daily (
    trade_date     DATE           NOT NULL,
    rank_no        SMALLINT       NOT NULL COMMENT 'rank 는 MySQL 8 예약어라 rank_no 를 쓴다',
    symbol         VARCHAR(20)    NOT NULL,
    trading_amount DECIMAL(24, 4) NULL COMMENT '거래대금',
    trading_volume BIGINT         NULL,
    last_price     DECIMAL(20, 4) NULL,
    change_rate    DECIMAL(9, 6)  NULL,
    ranked_at      DATETIME(6)    NOT NULL COMMENT 'API 가 알려준 집계 시각',
    collected_at   DATETIME(6)    NOT NULL,
    PRIMARY KEY (trade_date, rank_no),
    KEY idx_top30_daily_symbol (symbol, trade_date)
) COMMENT = '일별 거래대금 TOP30 스냅샷';
