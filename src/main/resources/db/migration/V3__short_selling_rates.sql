-- 공매도 실측 응답에 비율이 두 개다: 거래량 비중과 거래대금 비중.
--   "shortSellingVolumeRate": "0.05235", "shortSellingAmountRate": "0.0524"
-- V1 은 short_ratio 하나뿐이라 둘 중 하나를 버려야 했다. 이름도 실제 의미에 맞게 정리한다.
-- 소수 5자리까지 오므로 scale 도 4 -> 6 으로 넓힌다.
ALTER TABLE short_selling
    CHANGE COLUMN short_value short_amount DECIMAL(24, 4) NULL COMMENT '공매도 거래대금',
    CHANGE COLUMN short_ratio short_volume_rate DECIMAL(9, 6) NULL COMMENT '공매도 거래량 비중',
    ADD COLUMN short_amount_rate DECIMAL(9, 6) NULL COMMENT '공매도 거래대금 비중' AFTER short_volume_rate;
