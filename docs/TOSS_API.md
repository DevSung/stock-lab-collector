# 토스 오픈API 실측 메모

공식 문서에 없거나, 문서와 달랐던 것들을 실제 호출로 확인해 정리했다.
**여기 적힌 값은 전부 실측이다.** 추측으로 채운 항목은 없다.

Base URL: `https://openapi.tossinvest.com`

## 인증

```
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
grant_type=client_credentials&client_id=...&client_secret=...
```

**토큰은 클라이언트당 1개만 유효하다.** 새로 발급하면 이전 토큰이 즉시 죽는다.
유효기간은 **24시간**(실측: `expires_in` 기준).

그래서 발급 지점을 `TossTokenManager` 한 곳으로 좁혔다.

- single-flight: 이중 검사 잠금으로 동시 호출이 몰려도 발급은 1회
- 선제 갱신: 만료 5분 전부터 새 토큰으로 갈아탄다. 주기 갱신 스케줄러는 두지 않는다
  (재발급이 곧 기존 토큰 무효화라 유휴 상태에서 갈아치울 이유가 없다)
- CAS 재발급: `refreshIfSame(staleToken)` — 실패에 쓰인 토큰이 아직 캐시에 있을 때만 재발급.
  무조건 재발급하면 동시에 401을 받은 요청들이 서로를 revoke 하는 자멸 루프가 생긴다

## 에러 응답이 세 가지 형태로 온다

```
/api/v1/**     { "error": { "requestId", "code", "message", "data" } }
/oauth2/token  { "error": "invalid_client", "error_description": "..." }   OAuth2 표준
429            { "requestId", "code", "message" }                         래퍼 없음
```

`requestId`는 본문에 없으면 **`x-request-id` 헤더**에 있다.

에러 코드: `invalid-token` `expired-token` `token-revoked` `rate-limit-exceeded`
`stock-not-found` `unsupported-market` `invalid-request`

`invalid-request`는 `data.field`로 어느 필드가 문제인지 알려주고,
다른 필드가 전부 유효하면 **`data.allowedValues`로 허용값 목록까지 알려준다.**
파라미터를 모를 때 이 방법으로 알아냈다.

## rate limit

클라이언트 × API 그룹 단위 초당 제한. 응답 헤더로 `x-ratelimit-limit`, `x-ratelimit-remaining`, `x-ratelimit-reset` 확인 가능.

| 그룹 | 엔드포인트 | 문서 한도 | 이 프로젝트 | 간격 |
|---|---|---|---|---|
| `MARKET_DATA_CHART` | `/candles` | 20/s | 10/s | 100ms |
| `STOCK_TRADING_TREND` | `investor-trading`, `short-selling` | 10/s | 5/s | 200ms |
| `STOCK` | `/stocks`, `/rankings` | 5/s | 2.5/s | 400ms |
| `STOCK_ALL` | `/stocks/all` | 1/s | 0.5/s | 2초 |

**고정 윈도우 대신 균등 간격 페이싱을 쓴다.**
"1초에 N개" 방식은 윈도우 끝에 N개 + 다음 윈도우 시작에 N개가 몰려 임의의 1초 구간 기준 2N개가 나간다.
서버가 슬라이딩 윈도우로 세면 한도 초과다. 간격을 강제하면 이 버스트 자체가 없어진다.

문서 한도의 절반만 쓰는 이유는 서버 집계 기준이 우리와 다를 수 있어서다.
`STOCK_ALL`은 특히 빡빡해서 **연속 호출하면 바로 429가 난다**(실측).

## 재시도

```
재시도 루프
  └ rate limiter 퍼밋 획득     ← 재시도마다 다시 얻는다
      └ 실제 호출
```

재시도가 rate limiter 안쪽에 있으면 재시도분이 한도 계산에서 빠져 429를 더 유발한다.

- `Retry-After`가 오면 지수 백오프보다 **우선**. 단 60초를 넘으면 재시도를 포기한다
  (배치가 몇 분 멈춰 있느니 실패로 끝내고 다음 주기에 맡긴다)
- 없으면 1초 → 2초 → 4초 + 지터 ±20%
- 429를 받으면 **그룹 전체**를 멈춘다. 재시도하는 쪽만 쉬면 다른 호출이 계속 429를 유발한다
- `stock-not-found`, `unsupported-market`, 인증 실패는 재시도하지 않는다
  (인증은 인터셉터가 이미 1회 재시도했고, 더 하면 토큰만 태운다)

## 엔드포인트별 실측 스펙

### GET /api/v1/candles

```
symbol=005930 & interval=1d & count=200 & adjusted=true & before=<ISO 일시>
```

- `count` 최대 **200**
- `before`는 **inclusive**, 응답의 `nextBefore`를 그대로 넘기면 다음 페이지
- 봉은 **최신순**

```json
{ "result": { "candles": [ { "timestamp": "2026-09-17T00:00:00.000+09:00",
                             "openPrice": "251500", "highPrice": "259000",
                             "lowPrice": "251000", "closePrice": "256000",
                             "volume": "11058213", "currency": "KRW" } ],
              "nextBefore": "2026-09-14T00:00:00.000+09:00" } }
```

주의할 점 세 가지:

1. **`result`로 한 겹 감싸져 있다.** 문서에는 없던 계층이다
2. **가격·거래량이 문자열로 온다.** 숫자가 아니다
3. 필드명이 `open`이 아니라 **`openPrice`**

`before`의 `+09:00`은 쿼리스트링에서 `%2B`로 인코딩해야 한다.
**실측 결과 토스 서버는 깨진 `+`(공백)도 관대하게 받아준다.** 그래도 규격대로 보낸다 —
이런 관대함이 사라지면 증상이 예외가 아니라 **조용히 어긋난 기간의 데이터**라서 잡기 어렵다.
최종 URI에 `%2B`가 들어있는지 assert 하는 회귀 테스트를 남겨뒀다.

### GET /api/v1/stocks/{symbol}/investor-trading, /short-selling

```
count=100 & until=2026-09-03
```

**캔들과 페이징 규약이 다르다.**

|  | 캔들 | 수급 |
|---|---|---|
| 커서 | `before` (ISO 일시) | `until` (날짜) |
| count 상한 | 200 | **100** |

`count=200`을 넣으면 `{"field":"count","constraint":{"min":1,"max":100}}`로 알려준다.

```json
{ "result": { "nextUntil": "2026-09-03",
              "records": [ { "date": "2026-09-17",
                             "individual": null,
                             "foreigner": { "buyVolume": "...", "sellVolume": "...", "netBuyVolume": "-718171" },
                             "institution": { "...", "breakdown": { ... } },
                             "otherCorporation": null,
                             "foreignerHolding": { ... } } ] } }
```

- **당일 레코드는 일부 주체가 `null`이다.** 장 마감 전이라 미확정이다
- `institution.breakdown`에 금융투자·보험 등 세부 내역이 더 있다(현재 저장하지 않음)
- `foreignerHolding`(외국인 보유 수량·한도·비율)도 있다(현재 저장하지 않음)
- **공매도는 공시가 하루 늦다.** 일봉이 9/17까지 있을 때 공매도는 9/16이 최신이다
- 공매도 비율이 두 개다: `shortSellingVolumeRate`, `shortSellingAmountRate`
- 둘 다 국내 종목 전용. 해외 종목은 `unsupported-market`

### GET /api/v1/rankings

```
type=MARKET_TRADING_AMOUNT & marketCountry=KR & duration=1d & count=30
```

허용값(API가 직접 알려준 것):

- `type`: `MARKET_TRADING_AMOUNT`(거래대금) `MARKET_TRADING_VOLUME` `TOP_GAINERS` `TOP_LOSERS`
  `TOSS_SECURITIES_TRADING_AMOUNT` `TOSS_SECURITIES_TRADING_VOLUME`
- `marketCountry`: `KR` `US`
- `duration`: `realtime` `1d` `1w` `1mo` `3mo` `6mo` `1y`

처음에 `TRADING_VALUE`로 찍었다가 틀렸다. 추측했으면 틀렸을 부분이다.

```json
{ "result": { "rankedAt": "2026-09-17T14:22:21.305+09:00",
              "rankings": [ { "rank": 1, "symbol": "000660", "currency": "KRW",
                              "price": { "lastPrice": "1756000", "basePrice": "1759000",
                                         "changeRate": "-0.0017" },
                              "tradingVolume": "3055099",
                              "tradingAmount": "5364181931000" } ] } }
```

### GET /api/v1/stocks/all

```
market=KOSPI
```

허용값: `KOSPI` `KOSDAQ` `NYSE` `NASDAQ` `AMEX` `KR_ETC` `US_ETC`

```json
{ "result": [ { "symbol": "000020", "name": "동화약품",
                "securityType": "STOCK", "isCommonShare": true,
                "isinCode": "KR7000020008" } ] }
```

- **`result`가 객체가 아니라 배열이다**(다른 엔드포인트와 다르다)
- KOSPI 2,481건 / KOSDAQ 1,820건이 한 번에 온다. 페이징 없음
- **통화·상장상태 필드가 없다.** 통화는 market으로 정하고, 상장상태는 목록 포함 여부로 판정한다
  (목록에서 사라지면 폐지)

### GET /api/v1/stocks?symbols=005930

최대 200개까지 콤마로 구분. `stocks/all`보다 필드가 많다 —
`market` `status` `listDate` `delistDate` `sharesOutstanding` `koreanMarketDetail` 등.
현재 종목 마스터는 `stocks/all`만 쓰고 이 엔드포인트는 쓰지 않는다.
