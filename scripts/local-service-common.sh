#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_SUPPORT_DIR="$HOME/Library/Application Support/stock_monitor/local-services"
RUNTIME_ENV_FILE="$APP_SUPPORT_DIR/runtime.env"
DEFAULT_SERVICES=(stock-web a-stock-mcp)

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

strip_wrapping_quotes() {
  local value="$1"
  if (( ${#value} >= 2 )); then
    if [[ "${value:0:1}" == "\"" && "${value: -1}" == "\"" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  printf '%s' "$value"
}

ensure_runtime_dirs() {
  mkdir -p "$APP_SUPPORT_DIR"
}

find_env_source() {
  local candidate
  for candidate in \
    "$ROOT_DIR/.env" \
    "$ROOT_DIR/stock-web/src/main/resources/.env" \
    "$ROOT_DIR/us-stock-mcp/src/main/resources/.env" \
    "$ROOT_DIR/a-stock-mcp/src/main/resources/.env"; do
    if [[ -f "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

sync_runtime_env_file() {
  ensure_runtime_dirs

  local source_env
  if ! source_env="$(find_env_source)"; then
    echo "Missing .env file. Please create / update the project .env first." >&2
    exit 1
  fi

  local tmp_file="$APP_SUPPORT_DIR/runtime.env.tmp"
  umask 077
  : >"$tmp_file"

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    raw_line="${raw_line%$'\r'}"
    local line
    line="$(trim "$raw_line")"

    [[ -z "$line" ]] && continue
    [[ "$line" == \#* ]] && continue

    if [[ "$line" == export[[:space:]]* ]]; then
      line="${line#export }"
      line="$(trim "$line")"
    fi

    [[ "$line" != *=* ]] && continue

    local key="${line%%=*}"
    local value="${line#*=}"
    key="$(trim "$key")"
    value="$(trim "$value")"
    value="$(strip_wrapping_quotes "$value")"

    [[ -z "$key" ]] && continue
    [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] && continue

    printf 'export %s=%q\n' "$key" "$value" >>"$tmp_file"
  done <"$source_env"

  printf 'export SPRING_PROFILES_ACTIVE=%q\n' "${SPRING_PROFILES_ACTIVE:-prod}" >>"$tmp_file"
  mv "$tmp_file" "$RUNTIME_ENV_FILE"
}

normalize_services() {
  if (($# == 0)); then
    printf '%s\n' "${DEFAULT_SERVICES[@]}"
    return 0
  fi

  local arg
  local -a services=()
  for arg in "$@"; do
    case "$arg" in
      all)
        services=(stock-web a-stock-mcp us-stock-mcp)
        ;;
      stock-web|a-stock-mcp|us-stock-mcp)
        services+=("$arg")
        ;;
      *)
        echo "Unknown service: $arg" >&2
        exit 1
        ;;
    esac
  done

  printf '%s\n' "${services[@]}"
}

service_label() {
  case "$1" in
    stock-web) printf 'com.dawei.stock-monitor.stock-web\n' ;;
    a-stock-mcp) printf 'com.dawei.stock-monitor.a-stock-mcp\n' ;;
    us-stock-mcp) printf 'com.dawei.stock-monitor.us-stock-mcp\n' ;;
    *) return 1 ;;
  esac
}

service_port() {
  case "$1" in
    stock-web) printf '8888\n' ;;
    a-stock-mcp) printf '8091\n' ;;
    us-stock-mcp) printf '8090\n' ;;
    *) return 1 ;;
  esac
}

service_jar() {
  case "$1" in
    stock-web) printf '%s/stock-web/target/stock-web-1.0-SNAPSHOT.jar\n' "$ROOT_DIR" ;;
    a-stock-mcp) printf '%s/a-stock-mcp/target/a-stock-mcp-1.0-SNAPSHOT.jar\n' "$ROOT_DIR" ;;
    us-stock-mcp) printf '%s/us-stock-mcp/target/us-stock-mcp-1.0-SNAPSHOT.jar\n' "$ROOT_DIR" ;;
    *) return 1 ;;
  esac
}

service_home() {
  printf '%s/%s\n' "$APP_SUPPORT_DIR" "$1"
}

service_stdout_log() {
  printf '%s/stdout.log\n' "$(service_home "$1")"
}

service_stderr_log() {
  printf '%s/stderr.log\n' "$(service_home "$1")"
}

prepare_service_bundle() {
  local name="$1"
  local jar_source
  jar_source="$(service_jar "$name")"

  if [[ ! -f "$jar_source" ]]; then
    echo "Missing jar: $jar_source" >&2
    echo "Run the start script with --build first." >&2
    exit 1
  fi

  local home_dir
  home_dir="$(service_home "$name")"
  mkdir -p "$home_dir"

  cp "$jar_source" "$home_dir/app.jar"
  : >"$(service_stdout_log "$name")"
  : >"$(service_stderr_log "$name")"

  cat >"$home_dir/run.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
source "$RUNTIME_ENV_FILE"
cd "$home_dir"
exec /usr/bin/caffeinate -ims /usr/bin/java -jar "$home_dir/app.jar"
EOF
  chmod 700 "$home_dir/run.sh"
}

launchctl_print_service() {
  local label="$1"
  launchctl print "gui/$(id -u)/$label" 2>/dev/null || launchctl print "user/$(id -u)/$label" 2>/dev/null
}

service_pid_from_launchctl() {
  local label="$1"
  local pid
  pid="$(launchctl_print_service "$label" 2>/dev/null | awk '/pid = / {print $3; exit}')"
  if [[ -n "$pid" ]]; then
    printf '%s\n' "$pid"
  fi
}
