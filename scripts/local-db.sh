#!/usr/bin/env bash
# A private, non-root PostgreSQL cluster removes manual database setup locally.
# The application login owns only its database; TCP requires a generated password.
DB_STARTED=0
prepare_local_database() {
  local purpose="$1" port="$2" state_hash admin_password
  [[ "$EUID" -ne 0 ]] || fail "Run start.sh/test.sh as your normal Linux user, not sudo. PostgreSQL refuses root."
  find_postgres
  DB_STATE="$CP_ROOT/.local/$purpose"
  mkdir -p "$DB_STATE"
  chmod 700 "$CP_ROOT/.local" "$DB_STATE"
  exec 9>"$DB_STATE/run.lock"
  flock -n 9 || fail "Another $purpose process is using this checkout. Stop it first."
  DB_DATA="$DB_STATE/data"
  state_hash="$(printf '%s' "$DB_STATE" | sha256sum | cut -c1-16)"
  DB_SOCKET="/tmp/trench-roster-$UID-$state_hash"
  [[ ! -L "$DB_SOCKET" ]] || fail "Unexpected symlink at database socket directory."
  mkdir -p "$DB_SOCKET"
  [[ -O "$DB_SOCKET" ]] || fail "Database socket directory belongs to another user."
  chmod 700 "$DB_SOCKET"
  DB_PORT="$port"
  [[ "$port" =~ ^[0-9]+$ && "$port" -ge 1024 && "$port" -le 65535 ]] || fail "Invalid database port."
  if [[ ! -f "$DB_STATE/password" ]]; then
    [[ ! -f "$DB_DATA/PG_VERSION" ]] || fail "Database password file is missing; restore it from your backup."
    (umask 077; od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "$DB_STATE/password")
  fi
  if [[ ! -f "$DB_DATA/PG_VERSION" ]]; then
    admin_password="$(mktemp "$DB_STATE/admin.XXXXXX")"
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "$admin_password"
    "$PG_BIN/initdb" -D "$DB_DATA" -U cp1_admin --encoding=UTF8 --locale=C \
      --auth-local=trust --auth-host=scram-sha-256 --pwfile="$admin_password" >/dev/null || {
        rm -f "$admin_password"; fail "PostgreSQL initialization failed.";
      }
    rm -f "$admin_password"
    # Only the private Unix socket accepts the administrative login.
    printf 'local all all trust\nhost all cp1_admin 127.0.0.1/32 reject\nhost all all 127.0.0.1/32 scram-sha-256\n' > "$DB_DATA/pg_hba.conf"
  fi
  local installed_major
  installed_major="$("$PG_BIN/postgres" --version | sed -E 's/.* ([0-9]+).*/\1/')"
  [[ "$(cat "$DB_DATA/PG_VERSION")" == "$installed_major" ]] || fail "PostgreSQL major version changed; migrate the existing data before continuing."
  if ! "$PG_BIN/pg_ctl" -D "$DB_DATA" status >/dev/null 2>&1; then
    "$PG_BIN/pg_ctl" -D "$DB_DATA" -l "$DB_STATE/postgres.log" \
      -o "-h 127.0.0.1 -p $port -k $DB_SOCKET -c shared_buffers=32MB -c max_connections=20" -w start 9>&- ||
      fail "Cannot start PostgreSQL. Check $DB_STATE/postgres.log; port $port may already be in use."
    DB_STARTED=1
  fi
  DB_PASSWORD="$(cat "$DB_STATE/password")"
  export DB_PASSWORD DB_USER=trench_roster DB_URL="jdbc:postgresql://127.0.0.1:$port/trench_roster"
  # Password arrives on stdin, not in command-line arguments or logs.
  {
    printf "\\set app_password '%s'\n" "$DB_PASSWORD"
    cat <<'SQL'
SELECT format('CREATE ROLE trench_roster LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'trench_roster') \gexec
SELECT 'CREATE DATABASE trench_roster OWNER trench_roster'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'trench_roster') \gexec
SQL
  } | "$PG_BIN/psql" -X -v ON_ERROR_STOP=1 -h "$DB_SOCKET" -p "$port" -U cp1_admin -d postgres >/dev/null
  PGPASSWORD="$DB_PASSWORD" "$PG_BIN/psql" -X -h 127.0.0.1 -p "$port" -U trench_roster -d trench_roster \
    -v ON_ERROR_STOP=1 -c 'SELECT 1' >/dev/null || fail "Database login failed."
  echo "Private PostgreSQL ready on 127.0.0.1:$port. Data persists in .local/$purpose/."
}
stop_local_database() {
  if [[ "$DB_STARTED" == 1 ]]; then
    "$PG_BIN/pg_ctl" -D "$DB_DATA" -m fast -w stop >/dev/null || true
  fi
}
