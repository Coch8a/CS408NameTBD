#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/scripts/common.sh"
cd "$CP_ROOT"
mode="${1:-run}"
case "$mode" in run|--check|--build-only) ;; *) fail "Usage: ./start.sh [--check|--build-only]" ;; esac
check_build_tools
if [[ "$mode" != --build-only ]]; then find_postgres; fi
[[ "$mode" == --check ]] && { echo "Prerequisites OK."; exit 0; }
build_app
[[ "$mode" == --build-only ]] && exit 0
source "$CP_ROOT/scripts/local-db.sh"
app_pid=""
cleanup() {
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
    kill "$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true
  fi
  stop_local_database
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
prepare_local_database app "${CP1_DB_PORT:-55432}"
echo "Starting app; Flyway creates tables and catalogue data automatically."
echo "Open http://localhost:8080 after Spring reports Started. Ctrl+C stops this app and its local database."
if [[ "${SERVER_ADDRESS:-127.0.0.1}" == 0.0.0.0 ]]; then
  echo "WSL direct address: http://$(hostname -I | awk '{print $1}'):8080/"
fi
java -Xms64m -Xmx256m -jar target/trench-roster.jar 9>&- &
app_pid=$!
wait "$app_pid"
