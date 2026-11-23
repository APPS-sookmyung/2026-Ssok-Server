#!/usr/bin/env bash
# 서버에서 실행: 새 이미지를 받아 Spring 서버만 교체하고 상태 확인에 실패하면 이전 이미지로 되돌린다.
#   SERVER_IMAGE=ghcr.io/apps-sookmyung/2026-ssok-server:abc1234 ./deploy/deploy.sh
set -euo pipefail

cd "$(dirname "$0")/.."
: "${SERVER_IMAGE:?SERVER_IMAGE is required}"

STATE_FILE=.deployed-image
PREVIOUS_IMAGE=$(cat "$STATE_FILE" 2>/dev/null || true)
HEALTH_URL="http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health"

wait_healthy() {
  for _ in $(seq 1 30); do
    if curl -fsS "$HEALTH_URL" | grep -q '"UP"'; then
      return 0
    fi
    sleep 3
  done
  return 1
}

echo "Deploying $SERVER_IMAGE"
export SERVER_IMAGE
docker compose pull server
docker compose up -d --no-build mysql pgvector
docker compose up -d --no-build --no-deps server

if wait_healthy; then
  echo "$SERVER_IMAGE" > "$STATE_FILE"
  docker image prune -f > /dev/null
  echo "Deploy succeeded"
  exit 0
fi

echo "Health check failed" >&2
docker compose logs --tail=100 server >&2 || true
if [ -n "$PREVIOUS_IMAGE" ]; then
  echo "Rolling back to $PREVIOUS_IMAGE" >&2
  SERVER_IMAGE="$PREVIOUS_IMAGE" docker compose up -d --no-build --no-deps server
  wait_healthy && echo "Rollback succeeded" >&2
fi
exit 1
