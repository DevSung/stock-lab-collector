# stock-lab

토스증권 오픈API에서 주식 데이터를 받아 로컬 MySQL에 쌓는 **수집기**다.

분석과 예측 모델은 파이썬으로 따로 만든다. 이 프로젝트의 책임은 **"모델에 먹일 데이터를 정확하게, 구멍 없이, 반복 가능하게 쌓는 것"** 까지다.
여기엔 예측 로직도, 지표 계산도, 백테스트도 없다.

```
 토스 오픈API  ──►  stock-lab (Kotlin)  ──►  MySQL  ──►  파이썬 (pandas / 모델링)
                    이 프로젝트                          별도 프로젝트
```

## 무슨 데이터가 쌓이나

| 테이블 | 내용 | 키 |
|---|---|---|
| `daily_candle` | 일봉 (시·고·저·종가, 거래량). 수정주가 | 종목 + 거래일 |
| `investor_trading` | 투자자별 순매수 (개인·외국인·기관·기타법인) | 종목 + 거래일 |
| `short_selling` | 공매도 거래량·거래대금·비중 | 종목 + 거래일 |
| `top30_daily` | 일별 거래대금 TOP30 순위 스냅샷 | 거래일 + 순위 |
| `stock` | 종목 마스터 (이름·시장·종목구분·상장상태) | 종목 |
| `watchlist` | 수집 대상 종목 목록 | 종목 + 출처 + 카테고리 |
| `collection_log` | 수집 작업 이력 | 자동증가 |

**전체 종목이 아니라 `watchlist`에 있는 종목만 모은다.** 목록은 두 군데서 채워진다.

- `src/main/resources/watchlist/*.yml` — 내가 직접 정한 카테고리 (반도체, 2차전지 등)
- 매일 갱신되는 거래대금 TOP30

## 파이썬에서 꺼내 쓰기

```python
import pandas as pd
from sqlalchemy import create_engine

engine = create_engine("mysql+pymysql://stocklab:stocklab@localhost:13306/stock_lab")

candles = pd.read_sql(
    "SELECT symbol, trade_date, open_price, high_price, low_price, close_price, volume "
    "FROM daily_candle ORDER BY symbol, trade_date",
    engine, parse_dates=["trade_date"],
)
```

**모델을 만들기 전에 [docs/DATA.md](docs/DATA.md)를 꼭 읽을 것.**
데이터 사전과 함께, 이 데이터로 예측 모델을 만들 때 걸리기 쉬운 함정을 정리해뒀다 —
공매도 하루 지연으로 생기는 look-ahead bias, TOP30 선택 편향, 수정주가 재계산 문제 같은 것들이다.

## 빠른 시작

```bash
docker compose up -d                                          # MySQL (호스트 13306)
cp .env.example .env && open -e .env                          # 자격증명 입력
./scripts/run.sh --spring.profiles.active=tokencheck          # 연결 확인
./scripts/run.sh --spring.profiles.active=job --job=daily     # 한 번 수집해보기
```

요구사항: JDK 21, Docker, 토스증권 Open API 자격증명.

## 자격증명 설정

### 1. client_id / client_secret 발급

토스증권 WTS > **설정 > Open API**.

### 2. 허용 IP 등록 (미등록 IP는 403)

```bash
curl -s https://ifconfig.me
```

여기서 나온 IP를 WTS의 허용 IP 목록에 등록한다.
가정용 회선은 공인 IP가 바뀔 수 있다. **403이 나면 여기부터 의심할 것.**

### 3. .env 작성

```bash
cp .env.example .env
```

```
TOSS_CLIENT_ID=발급받은_client_id
TOSS_CLIENT_SECRET=발급받은_client_secret
```

이 파일은 `.gitignore`에 있어 커밋되지 않는다.
`application.yml`에는 `${TOSS_CLIENT_ID}` 플레이스홀더만 있고 기본값이 없어서, 환경변수가 없으면 **기동 자체가 실패**한다(의도된 fail-fast).

`./scripts/run.sh`가 `.env`를 읽어 환경변수로 올려준다. IDE에서 직접 실행할 때는 실행 구성의 환경변수에 같은 값을 넣으면 된다.

## 실행

### 상주 수집기

```bash
./scripts/run.sh --spring.profiles.active=scheduler
curl -s localhost:18080/actuator/health
```

스케줄(전부 KST):

| 배치 | 기본 시각 | 하는 일 |
|---|---|---|
| 종목 마스터 | 일요일 05:00 | KOSPI/KOSDAQ 전체 갱신, 목록에서 사라진 종목은 폐지 표시 |
| watchlist 갱신 | 평일 17:30 | 카테고리 yml 동기화 + 거래대금 TOP30 갱신 |
| 일일 수집 | 평일 18:00 | watchlist 종목의 최근 30일 일봉·투자자·공매도 |

시각은 `application.yml`의 `stocklab.schedule`에서 바꾼다.
cron 값은 공백이 있어 `--args`로는 전달되지 않는다. 환경변수를 쓰거나 yml을 고칠 것.

```bash
STOCKLAB_SCHEDULE_DAILYCOLLECTCRON="0 30 18 * * MON-FRI" ./scripts/run.sh --spring.profiles.active=scheduler
```

### 배치를 지금 한 번 돌리기

스케줄러가 도는 것과 **똑같은 작업**이다. 검증하거나 수동 재처리할 때 쓴다.

```bash
./scripts/run.sh --spring.profiles.active=job --job=daily      # 일일 수집
./scripts/run.sh --spring.profiles.active=job --job=watchlist  # TOP30 + 카테고리
./scripts/run.sh --spring.profiles.active=job --job=master     # 종목 마스터
./scripts/run.sh --spring.profiles.active=job --job=log        # 최근 수집 이력
```

### 과거 데이터 초기 적재 / 재수집

같은 기간을 다시 돌리면 덮어쓴다. 액면분할로 수정주가가 재계산됐을 때 이렇게 복구한다.

```bash
./scripts/run.sh --spring.profiles.active=collect --symbol=005930 --from=2020-01-01
./scripts/run.sh --spring.profiles.active=trend   --symbol=005930 --from=2024-01-01
./scripts/run.sh --spring.profiles.active=trend   --watchlist --days=90
```

### 단발 확인용

```bash
./scripts/run.sh --spring.profiles.active=tokencheck            # 토큰 발급만
./scripts/run.sh --spring.profiles.active=candledemo --count=5  # 일봉을 화면에 출력
```

## 문제 해결

기동 시 토큰을 미리 발급하므로 자격증명 문제는 **기동 즉시** 드러난다.

| 증상 | 원인 |
|---|---|
| `Could not resolve placeholder 'TOSS_CLIENT_ID'` | 환경변수 미주입. `.env` 작성 여부 확인 |
| `status=401, code=invalid_client` | client_id / client_secret 이 틀림 |
| `status=403 ... 허용 IP ...` | 현재 공인 IP가 WTS 허용 목록에 없음 |
| `code=token-revoked` | 같은 client_id로 다른 프로세스가 토큰을 새로 발급함 |
| `Connection refused` | MySQL이 꺼짐 → `docker compose up -d` |

에러 메시지 끝의 `requestId=...`는 토스 문의 시 필요한 값이다.

수집이 제대로 돌았는지는 DB로 확인한다.

```sql
SELECT job_name, status, started_at, finished_at, message
FROM collection_log ORDER BY started_at DESC LIMIT 10;
```

## 로컬 환경 격리

기존에 돌고 있는 다른 프로젝트와 충돌하지 않도록 포트를 비켜 잡았다.

| 대상 | 포트 | 비고 |
|---|---|---|
| 애플리케이션 | **18080** | 8080은 다른 프로젝트가 사용 |
| MySQL (docker) | **13306** | 3306은 로컬 설치 mysqld가 사용 |
| 스키마 | `stock_lab` | 데이터는 `./data/mysql` (gitignore) |

## 토큰 취급 주의

**토스 토큰은 클라이언트당 1개만 유효하다.** 새로 발급하면 이전 토큰이 즉시 무효화된다(`token-revoked`).

- 발급 지점은 `TossTokenManager` 하나뿐이다. ArchUnit 규칙으로 호출 지점 확산을 막고 있다
- **같은 client_id로 두 프로세스를 동시에 띄우지 말 것.** IDE 실행과 스케줄러가 겹치면 서로를 무효화한다
- 테스트는 `toss.auth.eager-init: false`로 토큰 발급을 막아둔다

## 프로젝트 구조 (심플 DDD)

컨텍스트별로 나누고, 각 컨텍스트 안을 세 계층으로 쪼갠다.

```
dev.teolab.stocklab
├── bootstrap/     CLI 러너 (프로파일별로 하나씩)
├── schedule/      배치 스케줄 트리거와 잡 본체
├── stock/         종목 마스터
├── market/        일봉 · 수급
├── watchlist/     수집 대상 종목 · TOP30
├── collection/    수집 이력
└── toss/          토스 오픈API 연동 (인증 · rate limit · 재시도 · 클라이언트)
```

각 컨텍스트 안:

```
domain/          순수 Kotlin. Spring·Jackson·slf4j 를 모른다. 포트(인터페이스)가 여기 있다
application/     유스케이스. 도메인만 안다
infrastructure/  Spring · RestClient · JDBC. 안쪽을 참조해도 된다
```

계층 규칙은 `LayerDependencyTest`(ArchUnit)가 강제한다.

### JPA 를 쓰지 않는 이유

적재는 전부 `JdbcTemplate` 배치 UPSERT다. JPA 의 `merge` 는 건당 SELECT 를 먼저 날려
200건 페이지마다 200번 왕복하고, `ON DUPLICATE KEY UPDATE` 는 MySQL 전용 문법이라 JPA 로 표현되지 않는다.

한때 `ddl-auto: validate` 로 스키마를 검사할 목적으로 엔티티를 뒀는데 걷어냈다.
`validate` 는 매핑된 엔티티만 검사해서 테이블 7개 중 1개만 보호했고,
정작 실제 위험인 **손으로 쓴 SQL 의 컬럼명 오타는 잡지 못했다.**
그 역할은 실제 MySQL 에 붙는 통합 테스트(`*IT.kt`)가 한다 — 테이블 7개 전부 커버한다.

토스 API의 실측 스펙과 rate limit·재시도 설계는 [docs/TOSS_API.md](docs/TOSS_API.md)에 있다.

## 테스트

```bash
docker compose up -d   # 통합 테스트가 실제 MySQL 을 쓴다
./gradlew build
```

실제 토스 API는 호출하지 않는다. HTTP 경계는 `MockRestServiceServer`와 가짜 execution으로 검증한다.
DB를 쓰는 통합 테스트는 `@Transactional`로 롤백한다 — 없으면 테스트가 실제 수집 데이터를 지운다.

## 알려진 한계

- **단일 인스턴스 전용.** 토큰이 클라이언트당 1개뿐이라 두 프로세스를 동시에 띄우면 서로를 무효화한다.
  운영 규칙으로만 막고 있고 코드로 강제하지는 않는다
- **스케줄러 스레드 풀이 1개다.** 일부러 늘리지 않았다. 배치가 겹치면 뒤엣것이 기다린다
- **과거 시점 스냅샷이 없다.** 모든 적재가 덮어쓰기라 "그때 알 수 있었던 값"을 복원할 수 없다.
  엄밀한 백테스트가 필요하면 [docs/DATA.md](docs/DATA.md)의 point-in-time 항목을 볼 것
- `ORM 없음.` 적재는 전부 손으로 쓴 SQL이다. 컬럼을 추가할 때 마이그레이션과 SQL 문자열을 같이 고쳐야 한다
- `stock.currency`는 마켓에서 유추한 값이다(`stocks/all` 응답에 통화 필드가 없다). 국내만 수집하므로 항상 KRW
- 상장폐지는 "전체 목록에서 사라짐"으로 판정한다. 마켓 조회가 하나라도 실패하면 폐지 표시를 건너뛴다
