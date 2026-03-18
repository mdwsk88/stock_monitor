#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/local-service-common.sh"

declare -a services=()
if (($# == 0)); then
  while IFS= read -r service; do
    services+=("$service")
  done < <(normalize_services)
else
  while IFS= read -r service; do
    services+=("$service")
  done < <(normalize_services "$@")
fi

stop_service() {
  local name="$1"
  local label
  label="$(service_label "$name")"
  local port
  port="$(service_port "$name")"
  local pid
  pid="$(service_pid_from_launchctl "$label" || true)"

  launchctl remove "$label" >/dev/null 2>&1 || true

  local waited=0
  while (( waited < 15 )); do
    if ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      if [[ -n "$pid" ]]; then
        echo "Stopped $name (PID $pid)"
      else
        echo "Stopped $name"
      fi
      return 0
    fi
    sleep 1
    ((waited += 1))
  done

  echo "Requested stop for $name, but port $port is still listening." >&2
  return 1
}

for service in "${services[@]}"; do
  stop_service "$service"
done
