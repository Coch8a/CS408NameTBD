#!/usr/bin/env bash
# Shared prerequisite and build functions; sourced by start.sh and test.sh.
CP_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail() { echo "ERROR: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "Missing $1. Install the prerequisites in README.md."; }
check_build_tools() {
  for tool in java javac mvn node npm curl flock realpath sha256sum; do need "$tool"; done
  local info version
  info="$(java -version 2>&1)"
  [[ "$info" =~ version\ \"21[.\"] ]] || fail "JDK 21 is required; check java -version."
  info="$(javac -version 2>&1)"
  [[ "$info" =~ javac\ 21([.]|$) ]] || fail "JDK 21 is required; check javac -version."
  info="$(mvn -version 2>&1)" || fail "Maven cannot start. Check JAVA_HOME."
  info="$(printf '%s\n' "$info" | sed $'s/\033\\[[0-9;]*m//g')"
  [[ "$info" =~ Java\ version:\ 21([.,]|$) ]] || fail "Maven must use Java 21; check JAVA_HOME."
  version="$(printf '%s\n' "$info" | sed -n 's/^Apache Maven \([0-9.]*\).*/\1/p')"
  [[ -n "$version" && "$(printf '%s\n' 3.6.3 "$version" | sort -V | head -n 1)" == 3.6.3 ]] || fail "Maven 3.6.3+ is required."
  node -e 'if(Number(process.versions.node.split(".")[0])!==24)process.exit(1)' ||
    fail "Node.js 24 is required. Run sudo bash deploy/install-node.sh."
}
find_postgres() {
  local candidate
  PG_BIN="${PG_BIN:-}"
  if [[ -z "$PG_BIN" ]]; then
    if command -v pg_ctl >/dev/null 2>&1; then
      PG_BIN="$(dirname "$(command -v pg_ctl)")"
    else
      for candidate in /usr/lib/postgresql/*/bin/pg_ctl; do
        [[ -x "$candidate" ]] && PG_BIN="$(dirname "$candidate")"
      done
    fi
  fi
  [[ -n "$PG_BIN" && -x "$PG_BIN/initdb" && -x "$PG_BIN/psql" ]] ||
    fail "PostgreSQL server tools are missing. Install postgresql and postgresql-client."
  local version
  version="$("$PG_BIN/postgres" --version | sed -E 's/.* ([0-9]+).*/\1/')"
  [[ "$version" =~ ^[0-9]+$ && "$version" -ge 16 ]] || fail "PostgreSQL 16+ is required."
}
build_frontend() {
  echo "Installing locked frontend dependencies..."
  (cd "$CP_ROOT/frontend" && npm ci --no-audit --no-fund && npm run build)
}
build_app() {
  build_frontend
  echo "Building Spring Boot..."
  (cd "$CP_ROOT" && mvn -B -ntp -DskipTests clean package)
}
