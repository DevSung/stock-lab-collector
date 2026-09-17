# 데이터 사전 · 파이썬에서 쓰기

이 문서는 **stock-lab이 쌓은 데이터로 예측 모델을 만들 사람**을 위한 것이다.
스키마 설명과, 이 데이터 특유의 함정을 정리했다.

> 함정 부분(맨 아래 "모델 만들 때 걸리는 것들")을 먼저 읽는 걸 권한다.
> 여기서 틀리면 백테스트 성적만 좋고 실전에서 안 맞는 모델이 나온다.

---

## 1. 연결

```python
import pandas as pd
from sqlalchemy import create_engine

engine = create_engine("mysql+pymysql://stocklab:stocklab@localhost:13306/stock_lab")
```

`pip install pandas sqlalchemy pymysql`

**DECIMAL 컬럼은 `Decimal` 객체로 온다.** 연산 전에 float으로 바꿔야 한다.

```python
price_cols = ["open_price", "high_price", "low_price", "close_price"]
candles[price_cols] = candles[price_cols].astype(float)
```

---

## 2. 테이블

### daily_candle — 일봉

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `symbol` | varchar(20) | | 종목코드 (`005930`) |
| `trade_date` | date | | 거래일 (**KST 기준**) |
| `open_price` `high_price` `low_price` `close_price` | decimal(20,4) | | 시·고·저·종가 |
| `volume` | bigint | | 거래량 (주) |
| `currency` | char(3) | O | `KRW` |
| `adjusted` | bool | | 수정주가 여부. 항상 `true` |
| `collected_at` | datetime(6) | | 이 행을 마지막으로 쓴 시각 (KST) |

PK: `(symbol, trade_date)` · 보조 인덱스: `trade_date`

거래일만 들어 있다. 주말·공휴일·거래정지일은 행 자체가 없다.

### investor_trading — 투자자별 순매수

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `symbol` `trade_date` | | | PK |
| `individual_net` | bigint | **O** | 개인 순매수 **수량**(주) |
| `foreign_net` | bigint | **O** | 외국인 순매수 수량 |
| `institution_net` | bigint | **O** | 기관 순매수 수량 |
| `other_corporation_net` | bigint | **O** | 기타법인 순매수 수량 |
| `collected_at` | datetime(6) | | |

**금액이 아니라 수량이다.** 양수면 순매수, 음수면 순매도.
장 마감 전에 수집하면 일부 주체가 `NULL`로 들어온다(아래 함정 2번).

국내 종목만 제공된다.

### short_selling — 공매도

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `symbol` `trade_date` | | | PK |
| `short_volume` | bigint | O | 공매도 거래량 (주) |
| `short_amount` | decimal(24,4) | O | 공매도 거래대금 (원) |
| `short_volume_rate` | decimal(9,6) | O | 거래량 대비 비중 (`0.05235` = 5.235%) |
| `short_amount_rate` | decimal(9,6) | O | 거래대금 대비 비중 |
| `collected_at` | datetime(6) | | |

비율은 **소수**다. 퍼센트가 아니다.
ETF는 공매도가 아예 없어서 행이 안 생기는 경우가 많다.
국내 종목만 제공되고, **공시가 하루 늦다**(함정 1번).

### top30_daily — 거래대금 TOP30 스냅샷

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `trade_date` | date | 거래일 |
| `rank_no` | smallint | 순위 1~30 (`rank`는 MySQL 예약어라 이 이름) |
| `symbol` | varchar(20) | 종목코드 |
| `trading_amount` | decimal(24,4) | 거래대금 (원) |
| `trading_volume` | bigint | 거래량 |
| `last_price` | decimal(20,4) | 집계 시점 가격 |
| `change_rate` | decimal(9,6) | 등락률 (소수) |
| `ranked_at` | datetime(6) | API가 알려준 집계 시각 |

PK: `(trade_date, rank_no)`

배치는 평일 17:30에 돈다. 장 마감(15:30) 이후라 그날 최종 순위로 본다.
다만 `ranked_at`을 확인할 것 — 수동으로 장중에 돌리면 장중 순위가 들어간다.

### stock — 종목 마스터

| 컬럼 | 설명 |
|---|---|
| `symbol` | PK |
| `name` | 종목명 |
| `market` | `KOSPI` / `KOSDAQ` |
| `security_type` | `STOCK` / `ETF` 등 |
| `isin_code` | 국제 증권 식별 번호 |
| `currency` | `KRW` (마켓에서 유추한 값) |
| `listing_status` | `ACTIVE` / `DELISTED` |
| `updated_at` | 마지막 동기화 시각 |

KOSPI·KOSDAQ 전 종목이 들어 있다(약 4,300개). 수집 대상과는 별개다.

### watchlist — 수집 대상

| 컬럼 | 설명 |
|---|---|
| `symbol` `source` `category` | PK |
| `source` | `TOP30` / `CATEGORY` / `MANUAL` |
| `category` | 카테고리명. TOP30·MANUAL은 빈 문자열 |
| `added_at` | 최초 등록 시각 |
| `last_seen_at` | 이 출처에서 마지막으로 확인된 시각 |

**한 종목이 여러 줄일 수 있다.** 삼성전자가 반도체 카테고리이면서 오늘 TOP30일 수 있다.
실제 수집 대상은 `SELECT DISTINCT symbol`이다.

TOP30에서 빠진 종목은 바로 지우지 않고 30일 유예 후 정리한다.

### collection_log — 수집 이력

| 컬럼 | 설명 |
|---|---|
| `job_name` | `daily-collect` / `watchlist-refresh` / `stock-master-sync` |
| `status` | `RUNNING` / `SUCCESS` / `FAILED` |
| `target_range` | 수집 대상 기간 |
| `message` | 결과 요약 또는 실패 사유 |
| `started_at` `finished_at` | KST |

데이터를 믿기 전에 여기부터 본다.

```sql
SELECT * FROM collection_log WHERE status <> 'SUCCESS' ORDER BY started_at DESC;
```

---

## 3. 모델 만들 때 걸리는 것들

### 함정 1. 공매도는 하루 늦게 공시된다 — look-ahead bias

**가장 사고나기 쉬운 지점이다.**

D일의 공매도 데이터는 D일 장중에 알 수 없다. **D+1일 저녁**에 공시된다.
그런데 DB에는 `trade_date = D`로 저장되어 있다. 그대로 조인하면 **미래 정보로 과거를 예측하는 모델**이 된다.

백테스트 성적은 훌륭하게 나오고 실전에서는 안 맞는다.

```python
short = short.sort_values(["symbol", "trade_date"])

# D일 종가 시점에 알 수 있는 공매도는 D-1일 것까지다
for col in ["short_volume", "short_volume_rate", "short_amount_rate"]:
    short[f"{col}_known"] = short.groupby("symbol")[col].shift(1)

features = candles.merge(
    short[["symbol", "trade_date", "short_volume_known", "short_volume_rate_known"]],
    on=["symbol", "trade_date"], how="left",
)
```

확인해보면 바로 보인다. 일봉은 오늘까지 있는데 공매도는 어제까지다.

```sql
SELECT (SELECT max(trade_date) FROM daily_candle)  AS candle_last,
       (SELECT max(trade_date) FROM short_selling) AS short_last;
```

### 함정 2. 당일 수급은 미확정이다

`investor_trading`의 당일 행은 장 마감 전이면 개인·기타법인이 `NULL`로 온다.
배치는 18:00에 돌아서 보통 확정값이지만, 수동으로 장중에 돌리면 미확정값이 들어간다.

`NULL`을 0으로 채우면 **"매매가 없었다"와 "아직 모른다"가 섞인다.** 전혀 다른 뜻이다.

```python
# 나쁨: 모른다는 걸 0으로 만들어버린다
df["individual_net"] = df["individual_net"].fillna(0)

# 나음: 미확정 행은 학습에서 뺀다
df = df.dropna(subset=["individual_net", "foreign_net", "institution_net"])
```

### 함정 3. 수정주가는 과거가 바뀐다

액면분할·무상증자가 일어나면 **그 종목의 과거 주가가 전부 재계산된다.**
5만원이던 작년 종가가 어느 날 1만원으로 바뀐다. 이 프로젝트는 그래서 덮어쓰기(UPSERT)로 적재한다.

수익률 계산에는 오히려 이게 맞다(분할 때문에 -80% 수익률이 찍히는 걸 막아준다).
문제는 **재현성**이다. 오늘 뽑은 데이터셋과 6개월 뒤 뽑은 데이터셋이 다를 수 있다.

→ 학습에 쓴 데이터셋은 parquet 등으로 **스냅샷을 떠서 보관할 것.**

### 함정 4. point-in-time 복원이 안 된다

DB는 항상 "지금 기준 최신값"만 들고 있다. 어떤 행이 언제 어떤 값이었는지는 남지 않는다.
`collected_at`은 **마지막으로 쓴 시각**일 뿐 이력이 아니다.

"2025년 3월 10일에 내가 알 수 있었던 값"으로 백테스트하려면 그 시점 스냅샷이 필요한데, 지금은 없다.
필요하다면 수집 때마다 이력 테이블에 쌓는 구조를 추가해야 한다.

실무적 타협: 함정 1·2를 지켜서 시차만 정확히 맞춰도 대부분의 look-ahead는 막힌다.

### 함정 5. 수집 대상이 전체 종목이 아니다 — 선택 편향

`watchlist`에 있는 종목만 모은다. 여기엔 두 겹의 편향이 있다.

**TOP30은 "오늘 거래대금이 터진 종목"이다.** 뭔가 일이 생겨서 들어온 종목이다.
이 표본으로 학습하면 "거래대금이 폭발한 종목"이라는 조건이 이미 걸린 데이터로 배우게 된다.
전체 시장에 그대로 적용하면 안 맞는다.

**카테고리 종목은 내가 고른 것이다.** 내가 아는 종목, 이미 살아남은 종목이 들어간다.

```sql
-- 내 데이터가 시장의 몇 %인지
SELECT (SELECT count(DISTINCT symbol) FROM daily_candle) AS collected,
       (SELECT count(*) FROM stock WHERE listing_status = 'ACTIVE') AS listed;
```

**상장폐지 종목도 빠져 있다.** 망한 종목을 빼고 학습하면 수익률이 과대평가된다(생존 편향).
`stock.listing_status = 'DELISTED'`를 확인하고, 필요하면 폐지 종목도 별도 수집할 것.

### 함정 6. 종목별로 기간에 구멍이 있다

TOP30에 들락날락한 종목은 추적하지 않던 기간이 통째로 비어 있다.
30일 유예 + 매일 30일치 재수집으로 대부분 메워지지만, **한 달 넘게 빠져 있던 종목은 구멍이 남는다.**

학습 전에 반드시 확인한다.

```python
coverage = (candles.groupby("symbol")["trade_date"]
            .agg(["min", "max", "count"])
            .assign(expected=lambda d: (d["max"] - d["min"]).dt.days * 5 / 7)
            .assign(ratio=lambda d: d["count"] / d["expected"]))

# 거래일 대비 80% 미만이면 구멍이 있다고 보고 들여다본다
print(coverage[coverage["ratio"] < 0.8])
```

구멍이 있으면 그 종목만 다시 받으면 된다.

```bash
./scripts/run.sh --spring.profiles.active=collect --symbol=005930 --from=2020-01-01
```

### 함정 7. 거래일 기준으로 다뤄야 한다

`daily_candle`에는 거래일만 있다. 달력 날짜로 `reindex`하면 주말·공휴일이 `NaN`으로 생기고,
그걸 `ffill` 하면 **휴장일에도 거래가 있었던 것처럼** 된다.

이동평균·수익률은 **행 기준(거래일 기준)** 으로 계산할 것.

```python
candles = candles.sort_values(["symbol", "trade_date"])
candles["ret_1d"] = candles.groupby("symbol")["close_price"].pct_change()
candles["ma_20"] = candles.groupby("symbol")["close_price"].transform(lambda s: s.rolling(20).mean())
```

거래정지된 종목도 그 기간 행이 없다. 휴장일과 구분하려면 같은 날 다른 종목에 행이 있는지 보면 된다.

### 함정 8. ETF가 섞여 있다

TOP30에는 `KODEX 200` 같은 ETF가 자주 든다. ETF는 개별 종목과 성격이 다르다 —
공매도가 없고, 수급 해석도 다르고, 펀더멘털이 없다.

```python
stocks = pd.read_sql("SELECT symbol, name, market, security_type FROM stock", engine)
equities = stocks[stocks["security_type"] == "STOCK"]
df = df[df["symbol"].isin(equities["symbol"])]
```

---

## 4. 바로 쓸 수 있는 조인

일봉 + 수급을 시차까지 맞춰 하나로 만드는 예시다.

```python
import pandas as pd
from sqlalchemy import create_engine

engine = create_engine("mysql+pymysql://stocklab:stocklab@localhost:13306/stock_lab")

def load(table, cols):
    df = pd.read_sql(f"SELECT {cols} FROM {table} ORDER BY symbol, trade_date",
                     engine, parse_dates=["trade_date"])
    for c in df.select_dtypes("object").columns:
        if c != "symbol":
            df[c] = pd.to_numeric(df[c], errors="ignore")
    return df

candles = load("daily_candle",
               "symbol, trade_date, open_price, high_price, low_price, close_price, volume")
investor = load("investor_trading",
                "symbol, trade_date, individual_net, foreign_net, institution_net")
short = load("short_selling", "symbol, trade_date, short_volume_rate, short_amount_rate")

# 공매도는 하루 늦게 공시되므로 한 거래일 밀어서 붙인다
short = short.sort_values(["symbol", "trade_date"])
short[["short_volume_rate", "short_amount_rate"]] = (
    short.groupby("symbol")[["short_volume_rate", "short_amount_rate"]].shift(1)
)

df = (candles
      .merge(investor, on=["symbol", "trade_date"], how="left")
      .merge(short, on=["symbol", "trade_date"], how="left")
      .sort_values(["symbol", "trade_date"]))

# 예측 대상: 다음 거래일 수익률
df["target_next_ret"] = df.groupby("symbol")["close_price"].pct_change().shift(-1)

# 미확정 수급이 섞인 행은 뺀다
df = df.dropna(subset=["individual_net", "foreign_net", "institution_net", "target_next_ret"])
```

만든 데이터셋은 스냅샷으로 남겨둘 것. 수정주가 재계산 때문에 나중에 재현이 안 될 수 있다.

```python
df.to_parquet(f"dataset_{pd.Timestamp.today():%Y%m%d}.parquet")
```
