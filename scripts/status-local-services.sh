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

for service in "${services[@]}"; do
  label="$(service_label "$service")"
  port="$(service_port "$service")"
  pid="$(service_pid_from_launchctl "$label" || true)"

  echo "[$service]"
  if [[ -n "$pid" ]] && lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "status: running"
    echo "pid: $pid"
    echo "port: $port"
  else
    echo "status: stopped"
    echo "port: $port"
  fi
  echo "stdout: $(service_stdout_log "$service")"
  echo "stderr: $(service_stderr_log "$service")"
  echo
done
