package dev.teolab.stocklab.market.domain

/** 일봉 저장 포트. 구현은 infrastructure. */
fun interface DailyCandleStore {

    /**
     * 같은 (종목, 거래일) 이 이미 있으면 **덮어쓴다.**
     *
     * 액면분할 등이 발생하면 수정주가가 재계산되어 과거 봉의 값이 바뀐다.
     * 그래서 무시(insert ignore)가 아니라 갱신이어야 한다.
     *
     * @return 실제로 삽입되거나 갱신된 건수
     */
    fun upsertAll(candles: List<DailyCandle>): Int
}
