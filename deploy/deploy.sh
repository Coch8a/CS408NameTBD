#!/usr/bin/env bash
set -euo pipefail

usage() { echo "Usage: ./deploy/deploy.sh -h PUBLIC-IP -i /path/to/key.pem"; }
fail() { echo "ERROR: $*" >&2; exit 1; }
host=""
key=""
while getopts ':h:i:' option; do
  case "$option" in
    h) host="$OPTARG" ;;
    i) key="$OPTARG" ;;
    *) usage; exit 1 ;;
  esac
done
[[ -n "$host" && -n "$key" ]] || { usage; exit 1; }
[[ "$host" =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ ]] || fail "Supply an IP or DNS hostname, without http://."
[[ -f "$key" ]] || fail "Private key not found: $key"
for tool in ssh rsync curl realpath mktemp; do
  command -v "$tool" >/dev/null 2>&1 || fail "Missing $tool; see README.md."
done
key="$(realpath "$key")"
cd "$(dirname "${BASH_SOURCE[0]}")/.."
./start.sh --check
echo "Running local HTTP endpoint tests..."
mvn -B -ntp verify

# Preserve spaces in the local key path when rsync invokes SSH.
ssh_wrapper="$(mktemp)"
trap 'rm -f "$ssh_wrapper"' EXIT
printf '#!/usr/bin/env bash\nexec ssh -i %q -o IdentitiesOnly=yes "$@"\n' "$key" > "$ssh_wrapper"
chmod 700 "$ssh_wrapper"
remote="ubuntu@$host"
"$ssh_wrapper" "$remote" 'test -d /opt/trench-roster/source' ||
  fail "Cannot access the app directory. Check SSH and run setup-ec2.sh first."

echo "Copying source to the dedicated deployment directory..."
# --delete applies only to this app's source mirror. Persistent data belongs
# outside it, in /var/lib/trench-roster or a separate database.
rsync -az --delete --chmod=D755,F644 -e "$ssh_wrapper" \
  --exclude='.git/' --exclude='target/' --exclude='*.pem' \
  --exclude='.env' --exclude='.env.*' --exclude='.idea/' \
  --exclude='*.log' --exclude='node_modules/' \
  ./ "$remote:/opt/trench-roster/source/"

echo "Building on EC2 and restarting the service..."
"$ssh_wrapper" "$remote" 'bash -se' <<'REMOTE'
set -euo pipefail
cd /opt/trench-roster/source
chmod +x start.sh deploy/*.sh
# Limit Maven's heap on the small class instance; the running app has its own cap.
export MAVEN_OPTS="-Xmx256m"
./start.sh --build-only
# Replace the JAR only after a successful build. The service runs as a separate user.
sudo install -m 644 target/trench-roster.jar /opt/trench-roster/app.jar.new
sudo mv /opt/trench-roster/app.jar.new /opt/trench-roster/app.jar
sudo systemctl restart trench-roster
REMOTE

echo "Waiting for the public health endpoint..."
for attempt in {1..60}; do
  status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --connect-timeout 3 --max-time 5 "http://$host/api/health" || true)"
  if [[ "$status" == 200 ]]; then
    echo "Deployment succeeded: http://$host/"
    exit 0
  fi
  sleep 2
done
echo "Health check failed. Check port 80 and the service logs." >&2
"$ssh_wrapper" "$remote" 'sudo journalctl -u trench-roster -n 60 --no-pager' || true
exit 1
