#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/deploy/nas"
DIST_DIR="$DEPLOY_DIR/dist"

NAS_HOST="${NAS_HOST:-root@nas.local}"
NAS_PORT="${NAS_PORT:-22}"
REMOTE_DIR="${REMOTE_DIR:-/volume1/docker/stock-monitor}"
REMOTE_DOCKER_BIN="${REMOTE_DOCKER_BIN:-/usr/local/bin/docker}"
SSH_STRICT_HOST_KEY_CHECKING="${SSH_STRICT_HOST_KEY_CHECKING:-accept-new}"
DOCKER_BUILD_PLATFORM="${DOCKER_BUILD_PLATFORM:-linux/amd64}"

ONLY="${ONLY:-both}"
SKIP_BUILD=0

STOCK_WEB_IMAGE="${STOCK_WEB_IMAGE:-stock-monitor-web:latest}"
A_STOCK_MCP_IMAGE="${A_STOCK_MCP_IMAGE:-stock-monitor-a-stock-mcp:latest}"

usage() {
  cat <<'EOF'
Usage: deploy/nas/deploy-to-nas.sh [options]

Build local images, export them to deploy/nas/dist, upload compose/env/image tar files
to Synology, load the images on NAS, start containers, and wait for healthy status.

Options:
  --only both|stock-web|a-stock-mcp
  --skip-build
  --host root@nas.local
  --port 22
  --remote-dir /volume1/docker/stock-monitor
  --docker-bin /usr/local/bin/docker
  --platform linux/amd64
  --stock-web-image stock-monitor-web:latest
  --a-stock-mcp-image stock-monitor-a-stock-mcp:latest
  -h, --help

Environment variables:
  NAS_HOST, NAS_PORT, REMOTE_DIR, REMOTE_DOCKER_BIN, DOCKER_BUILD_PLATFORM
  STOCK_WEB_IMAGE, A_STOCK_MCP_IMAGE, ONLY
EOF
}

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

fail() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

require_file() {
  [ -f "$1" ] || fail "Missing file: $1"
}

sanitize_fragment() {
  printf '%s' "$1" | sed 's#[^A-Za-z0-9._-]#-#g'
}

image_tar_name() {
  local image="$1"
  local repo tag base

  repo="${image%%:*}"
  tag="${image#*:}"
  if [ "$tag" = "$image" ]; then
    tag="latest"
  fi

  base="${repo##*/}"
  printf '%s-%s.tar' "$(sanitize_fragment "$base")" "$(sanitize_fragment "$tag")"
}

quote_for_remote() {
  printf '%q' "$1"
}

ssh_cmd() {
  ssh -p "$NAS_PORT" -o "StrictHostKeyChecking=$SSH_STRICT_HOST_KEY_CHECKING" "$NAS_HOST" "$@"
}

build_image() {
  local image="$1"
  shift

  if [ -n "$DOCKER_BUILD_PLATFORM" ]; then
    log "Building $image for $DOCKER_BUILD_PLATFORM"
    docker buildx build \
      --platform "$DOCKER_BUILD_PLATFORM" \
      --load \
      -t "$image" \
      "$@" \
      "$ROOT_DIR"
  else
    log "Building $image for local Docker platform"
    docker build -t "$image" "$@" "$ROOT_DIR"
  fi

  log "Built image metadata: $(docker image inspect "$image" --format '{{.Architecture}} {{.Created}}')"
}

build_stock_web() {
  build_image "$STOCK_WEB_IMAGE"
}

build_a_stock_mcp() {
  build_image "$A_STOCK_MCP_IMAGE" \
    --build-arg APP_MODULE=a-stock-mcp \
    --build-arg APP_PORT=8091
}

prepare_artifact() {
  local image="$1"
  local tar_name="$2"
  local tar_path="$DIST_DIR/$tar_name"

  if docker image inspect "$image" >/dev/null 2>&1; then
    log "Exporting $image -> $tar_path"
    docker save -o "$tar_path" "$image"
    shasum -a 256 "$tar_path" > "$tar_path.sha256"
    return 0
  fi

  if [ "$SKIP_BUILD" -eq 1 ] && [ -f "$tar_path" ]; then
    log "Reusing existing artifact $tar_path"
    shasum -a 256 "$tar_path" > "$tar_path.sha256"
    return 0
  fi

  fail "Local image not found and artifact missing: $image ($tar_path)"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --only)
      [ $# -ge 2 ] || fail "--only requires a value"
      ONLY="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --host)
      [ $# -ge 2 ] || fail "--host requires a value"
      NAS_HOST="$2"
      shift 2
      ;;
    --port)
      [ $# -ge 2 ] || fail "--port requires a value"
      NAS_PORT="$2"
      shift 2
      ;;
    --remote-dir)
      [ $# -ge 2 ] || fail "--remote-dir requires a value"
      REMOTE_DIR="$2"
      shift 2
      ;;
    --docker-bin)
      [ $# -ge 2 ] || fail "--docker-bin requires a value"
      REMOTE_DOCKER_BIN="$2"
      shift 2
      ;;
    --platform)
      [ $# -ge 2 ] || fail "--platform requires a value"
      DOCKER_BUILD_PLATFORM="$2"
      shift 2
      ;;
    --stock-web-image)
      [ $# -ge 2 ] || fail "--stock-web-image requires a value"
      STOCK_WEB_IMAGE="$2"
      shift 2
      ;;
    --a-stock-mcp-image)
      [ $# -ge 2 ] || fail "--a-stock-mcp-image requires a value"
      A_STOCK_MCP_IMAGE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

case "$ONLY" in
  both|stock-web|a-stock-mcp)
    ;;
  *)
    fail "--only must be one of: both, stock-web, a-stock-mcp"
    ;;
esac

require_cmd docker
require_cmd ssh
require_cmd tar
require_cmd shasum

docker info >/dev/null 2>&1 || fail "Local Docker daemon is not running"

log "Checking NAS connectivity"
ssh_cmd "test -x $(quote_for_remote "$REMOTE_DOCKER_BIN") && $(quote_for_remote "$REMOTE_DOCKER_BIN") version >/dev/null"

mkdir -p "$DIST_DIR"

STOCK_WEB_TAR_NAME="$(image_tar_name "$STOCK_WEB_IMAGE")"
A_STOCK_MCP_TAR_NAME="$(image_tar_name "$A_STOCK_MCP_IMAGE")"

TAR_CREATE_ARGS=(-cf -)
if tar --no-mac-metadata -cf /dev/null "$DEPLOY_DIR/README.md" >/dev/null 2>&1; then
  TAR_CREATE_ARGS=(--no-mac-metadata -cf -)
fi

TRANSFER_ITEMS=()

if [ "$ONLY" = "both" ] || [ "$ONLY" = "stock-web" ]; then
  require_file "$DEPLOY_DIR/docker-compose.yml"
  require_file "$DEPLOY_DIR/env/stock-web.env"
  if [ "$SKIP_BUILD" -eq 0 ]; then
    build_stock_web
  fi
  prepare_artifact "$STOCK_WEB_IMAGE" "$STOCK_WEB_TAR_NAME"
  TRANSFER_ITEMS+=(
    "docker-compose.yml"
    "env/stock-web.env"
    "dist/$STOCK_WEB_TAR_NAME"
    "dist/$STOCK_WEB_TAR_NAME.sha256"
  )
fi

if [ "$ONLY" = "both" ] || [ "$ONLY" = "a-stock-mcp" ]; then
  require_file "$DEPLOY_DIR/docker-compose.a-stock-mcp.yml"
  require_file "$DEPLOY_DIR/env/a-stock-mcp.env"
  if [ "$SKIP_BUILD" -eq 0 ]; then
    build_a_stock_mcp
  fi
  prepare_artifact "$A_STOCK_MCP_IMAGE" "$A_STOCK_MCP_TAR_NAME"
  TRANSFER_ITEMS+=(
    "docker-compose.a-stock-mcp.yml"
    "env/a-stock-mcp.env"
    "dist/$A_STOCK_MCP_TAR_NAME"
    "dist/$A_STOCK_MCP_TAR_NAME.sha256"
  )
fi

log "Uploading deployment bundle to $NAS_HOST:$REMOTE_DIR"
COPYFILE_DISABLE=1 tar -C "$DEPLOY_DIR" "${TAR_CREATE_ARGS[@]}" "${TRANSFER_ITEMS[@]}" \
  | ssh_cmd "mkdir -p $(quote_for_remote "$REMOTE_DIR") $(quote_for_remote "$REMOTE_DIR/env") $(quote_for_remote "$REMOTE_DIR/dist") && tar -C $(quote_for_remote "$REMOTE_DIR") -xf -"

REMOTE_ENV=(
  "REMOTE_DIR=$REMOTE_DIR"
  "REMOTE_DOCKER_BIN=$REMOTE_DOCKER_BIN"
  "ONLY=$ONLY"
  "STOCK_WEB_IMAGE=$STOCK_WEB_IMAGE"
  "A_STOCK_MCP_IMAGE=$A_STOCK_MCP_IMAGE"
  "STOCK_WEB_TAR_NAME=$STOCK_WEB_TAR_NAME"
  "A_STOCK_MCP_TAR_NAME=$A_STOCK_MCP_TAR_NAME"
)

REMOTE_PREFIX=""
for kv in "${REMOTE_ENV[@]}"; do
  REMOTE_PREFIX="$REMOTE_PREFIX $(quote_for_remote "$kv")"
done

log "Loading images and starting containers on NAS"
ssh -p "$NAS_PORT" -o "StrictHostKeyChecking=$SSH_STRICT_HOST_KEY_CHECKING" "$NAS_HOST" "$REMOTE_PREFIX sh -s" <<'REMOTE'
set -eu

compose_cmd() {
  case "$ONLY" in
    both)
      STOCK_WEB_IMAGE="$STOCK_WEB_IMAGE" \
      A_STOCK_MCP_IMAGE="$A_STOCK_MCP_IMAGE" \
      "$REMOTE_DOCKER_BIN" compose -f docker-compose.yml -f docker-compose.a-stock-mcp.yml "$@"
      ;;
    stock-web)
      STOCK_WEB_IMAGE="$STOCK_WEB_IMAGE" \
      "$REMOTE_DOCKER_BIN" compose -f docker-compose.yml "$@"
      ;;
    a-stock-mcp)
      A_STOCK_MCP_IMAGE="$A_STOCK_MCP_IMAGE" \
      "$REMOTE_DOCKER_BIN" compose -f docker-compose.a-stock-mcp.yml "$@"
      ;;
  esac
}

wait_for_health() {
  container="$1"
  url="$2"
  timeout_seconds="$3"
  start_ts="$(date +%s)"

  while :; do
    health_status="$("$REMOTE_DOCKER_BIN" inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$container" 2>/dev/null || printf 'missing')"

    if [ "$health_status" = "healthy" ]; then
      printf '%s healthy\n' "$container"
      curl -fsS "$url" >/dev/null
      return 0
    fi

    now_ts="$(date +%s)"
    if [ $((now_ts - start_ts)) -ge "$timeout_seconds" ]; then
      printf '%s did not become healthy in %ss (health=%s)\n' "$container" "$timeout_seconds" "$health_status" >&2
      "$REMOTE_DOCKER_BIN" logs --tail=80 "$container" >&2 || true
      return 1
    fi

    sleep 10
  done
}

cd "$REMOTE_DIR"

if [ "$ONLY" = "both" ] || [ "$ONLY" = "stock-web" ]; then
  "$REMOTE_DOCKER_BIN" load -i "$REMOTE_DIR/dist/$STOCK_WEB_TAR_NAME"
fi

if [ "$ONLY" = "both" ] || [ "$ONLY" = "a-stock-mcp" ]; then
  "$REMOTE_DOCKER_BIN" load -i "$REMOTE_DIR/dist/$A_STOCK_MCP_TAR_NAME"
fi

compose_cmd config >/tmp/stock-monitor-compose.yaml
compose_cmd up -d
compose_cmd ps

if [ "$ONLY" = "both" ] || [ "$ONLY" = "stock-web" ]; then
  wait_for_health stock-monitor-web http://127.0.0.1:8888/actuator/health 360
  printf 'stock-web actuator: %s\n' "$(curl -fsS http://127.0.0.1:8888/actuator/health)"
fi

if [ "$ONLY" = "both" ] || [ "$ONLY" = "a-stock-mcp" ]; then
  wait_for_health stock-monitor-a-stock-mcp http://127.0.0.1:8091/actuator/health 360
  printf 'a-stock-mcp actuator: %s\n' "$(curl -fsS http://127.0.0.1:8091/actuator/health)"
fi

compose_cmd ps
REMOTE

log "Deployment finished"
