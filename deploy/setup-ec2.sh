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
for file in trench-roster.service nginx-trench-roster.conf; do
  [[ -f "$script_dir/$file" ]] || { echo "Missing deploy/$file" >&2; exit 1; }
done

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y openjdk-21-jdk-headless maven nginx rsync curl ca-certificates
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
