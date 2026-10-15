#!/usr/bin/env bash
set -euo pipefail

[[ "$EUID" -eq 0 ]] || { echo "Run on EC2: sudo bash deploy/setup-ec2.sh" >&2; exit 1; }
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# This setup targets the dedicated Ubuntu 24.04 instance from assignment 07.01.
source /etc/os-release
[[ "$ID" == ubuntu && "$VERSION_ID" == 24.04 ]] || {
  echo "This setup script requires Ubuntu 24.04." >&2; exit 1;
}
id ubuntu >/dev/null 2>&1 || { echo "The ubuntu login account is missing." >&2; exit 1; }
for file in trench-roster.service nginx-trench-roster.conf install-node.sh; do
  [[ -f "$script_dir/$file" ]] || { echo "Missing deploy/$file" >&2; exit 1; }
done

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y openjdk-21-jdk-headless maven nginx rsync curl ca-certificates xz-utils postgresql postgresql-client
bash "$script_dir/install-node.sh"
# Make Java 21 the selected system Java on this dedicated app server.
java21_dir="$(find /usr/lib/jvm -maxdepth 1 -type d -name 'java-21-openjdk-*' -print -quit)"
[[ -n "$java21_dir" ]] || { echo "JDK 21 installation not found." >&2; exit 1; }
update-alternatives --set java "$java21_dir/bin/java"
update-alternatives --set javac "$java21_dir/bin/javac"

if ! id trench-roster >/dev/null 2>&1; then
  useradd --system --user-group --home-dir /var/lib/trench-roster \
    --shell /usr/sbin/nologin trench-roster
fi
install -d -m 755 /opt/trench-roster
install -d -m 755 -o ubuntu -g ubuntu /opt/trench-roster/source
install -d -m 750 -o trench-roster -g trench-roster /var/lib/trench-roster
# Store generated database credentials outside the source checkout; preserve on rerun.
systemctl enable --now postgresql
pg_lsclusters --no-header | while read -r version cluster port status owner data log; do
  if [[ "$status" != online* ]]; then pg_ctlcluster "$version" "$cluster" start; fi
done
install -d -m 700 /etc/trench-roster
if [[ ! -f /etc/trench-roster/environment ]]; then
  db_password="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
  (umask 077; printf 'DB_URL=jdbc:postgresql://127.0.0.1:5432/trench_roster\nDB_USER=trench_roster\nDB_PASSWORD=%s\n' "$db_password" > /etc/trench-roster/environment)
fi
# This root-owned file contains generated assignments, never downloaded input.
source /etc/trench-roster/environment
[[ "$DB_PASSWORD" =~ ^[a-f0-9]{64}$ ]] || { echo "Unexpected stored database credential format." >&2; exit 1; }
{
  printf "\\set app_password '%s'\n" "$DB_PASSWORD"
  cat <<'SQL'
SELECT format('CREATE ROLE trench_roster LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'trench_roster') \gexec
ALTER ROLE trench_roster WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
SELECT 'CREATE DATABASE trench_roster OWNER trench_roster'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'trench_roster') \gexec
SQL
} | runuser -u postgres -- psql -X -v ON_ERROR_STOP=1 -d postgres >/dev/null
PGPASSWORD="$DB_PASSWORD" psql -X -h 127.0.0.1 -U trench_roster -d trench_roster -v ON_ERROR_STOP=1 -c 'SELECT 1' >/dev/null
unset DB_PASSWORD db_password
install -m 644 "$script_dir/trench-roster.service" /etc/systemd/system/trench-roster.service
install -m 644 "$script_dir/nginx-trench-roster.conf" /etc/nginx/sites-available/trench-roster
# Replace Ubuntu's default welcome site on this dedicated instance.
rm -f /etc/nginx/sites-enabled/default
ln -sfn /etc/nginx/sites-available/trench-roster /etc/nginx/sites-enabled/trench-roster
nginx -t
systemctl daemon-reload
systemctl enable trench-roster
systemctl enable --now nginx
systemctl reload nginx

# Respect an existing UFW firewall without enabling it on a fresh machine.
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q '^Status: active'; then
  ufw allow OpenSSH
  ufw allow 'Nginx HTTP'
fi
echo "Setup complete. Deploy from your laptop next."
echo "The app service is enabled for boot; the first deploy starts it."
echo "Until that deploy, an nginx 502 response is expected."
