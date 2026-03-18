#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/local-service-common.sh"

build_if_needed=false
declare -a raw_args=()
for arg in "$@"; do
  if [[ "$arg" == "--build" ]]; then
    build_if_needed=true
  else
    raw_args+=("$arg")
  fi
done

declare -a services=()
if ((${#raw_args[@]} == 0)); then
  while IFS= read -r service; do
    services+=("$service")
  done < <(normalize_services)
else
  while IFS= read -r service; do
    services+=("$service")
  done < <(normalize_services "${raw_args[@]}")
fi

if [[ "$build_if_needed" == true ]]; then
  declare -A module_seen=()
  declare -a modules=()

  for service in "${services[@]}"; do
    case "$service" in
      stock-web) module="stock-web" ;;
      a-stock-mcp) module="a-stock-mcp" ;;
      us-stock-mcp) module="us-stock-mcp" ;;
      *) echo "Unknown service: $service" >&2; exit 1 ;;
    esac

    if [[ -z "${module_seen[$module]:-}" ]]; then
      module_seen[$module]=1
      modules+=("$module")
    fi
  done

  module_list="$(IFS=,; printf '%s' "${modules[*]}")"
  mvn -pl "$module_list" -am package -DskipTests
fi

sync_runtime_env_file

start_service() {
  local name="$1"
  local label
  label="$(service_label "$name")"
  local port
  port="$(service_port "$name")"

  prepare_service_bundle "$name"
  launchctl remove "$label" >/dev/null 2>&1 || true

  launchctl submit \
    -l "$label" \
    -o "$(service_stdout_log "$name")" \
    -e "$(service_stderr_log "$name")" \
    -- /bin/bash "$(service_home "$name")/run.sh"

  local waited=0
  while (( waited < 30 )); do
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      local pid
      pid="$(service_pid_from_launchctl "$label")"
      echo "Started $name on port $port (PID ${pid:-unknown})"
      echo "Logs:"
      echo "  stdout: $(service_stdout_log "$name")"
      echo "  stderr: $(service_stderr_log "$name")"
      return 0
    fi

    sleep 1
    ((waited += 1))
  done

  echo "Timed out waiting for $name to bind port $port." >&2
  echo "stderr log:" >&2
  tail -n 40 "$(service_stderr_log "$name")" >&2 || true
  echo "stdout log:" >&2
  tail -n 40 "$(service_stdout_log "$name")" >&2 || true
  exit 1
}

for service in "${services[@]}"; do
  start_service "$service"
done
