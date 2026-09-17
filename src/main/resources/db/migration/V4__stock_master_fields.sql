-- stocks/all 응답에 있는 두 필드를 보관한다.
--   securityType  STOCK / ETF 등 (ETF 는 공매도가 0 으로 오는 등 성격이 달라 구분이 필요하다)
--   isinCode      국제 증권 식별 번호
ALTER TABLE stock
    ADD COLUMN security_type VARCHAR(30) NULL COMMENT 'STOCK / ETF 등' AFTER market,
    ADD COLUMN isin_code     VARCHAR(20) NULL COMMENT '국제 증권 식별 번호' AFTER security_type,
    ADD KEY idx_stock_market_status (market, listing_status);
