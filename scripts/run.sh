#!/usr/bin/env bash
# .env 를 환경변수로 올린 뒤 애플리케이션을 실행한다.
#   ./scripts/run.sh                                      # 상주 실행
#   ./scripts/run.sh --spring.profiles.active=tokencheck  # 토큰 발급만 확인하고 종료
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a; . ./.env; set +a
else
  echo "경고: .env 가 없습니다. .env.example 을 복사해 자격증명을 채우세요." >&2
fi

: "${TOSS_CLIENT_ID:?TOSS_CLIENT_ID 가 비어 있습니다}"
: "${TOSS_CLIENT_SECRET:?TOSS_CLIENT_SECRET 가 비어 있습니다}"

exec ./gradlew bootRun --args="$*"
