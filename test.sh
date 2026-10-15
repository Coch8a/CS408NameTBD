#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/scripts/common.sh"
source "$CP_ROOT/scripts/local-db.sh"
cd "$CP_ROOT"
check_build_tools
find_postgres
build_frontend
(cd frontend && npm test)
trap stop_local_database EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
prepare_local_database test "${CP1_TEST_DB_PORT:-55433}"
export TEST_DB_URL="$DB_URL" TEST_DB_USER="$DB_USER" TEST_DB_PASSWORD="$DB_PASSWORD" TEST_DB_DRIVER=org.postgresql.Driver
mvn -B -ntp clean verify
echo "PASS: React tests and Java HTTP/schema/migration tests against real PostgreSQL."
