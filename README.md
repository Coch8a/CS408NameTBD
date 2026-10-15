# Trench Crusade Roster Builder

React + Bootstrap landing page, Spring Boot API, and PostgreSQL with a cuople initial
tables. The compact dark green/grey catalogue uses New Antioch costs from
Warbands of Trench Crusade 1.0.2, with search, equipment filters, and sorting. Registration/login is scheduled for Checkpoint 2; saved rosters and
ownership follow in Checkpoint 3. There are no accounts or roster editing quite yet.

## Updating the catalogue

Extract the update into the repository,  and run
`./test.sh`, then `SERVER_ADDRESS=0.0.0.0 ./start.sh`. The new V3 migration runs
automatically on both existing and fresh databases. Do not delete `.local/` or
edit the already applied V1/V2 migrations. Existing user and roster records are
preserved; old demo rows remain for referential integrity but are not returned
by the catalogue API. Refresh the browser with Ctrl+F5 after restarting.

The page displays an example of three units and fourteen example armoury entries,
not a complete list builder. Base costs: Lieutenant 70, Yeoman 30, Mechanized
Heavy Infantry 85 with Reinforced Armour or 95 with Machine Armour. The Yeoman
can buy a Bolt-Action Rifle for +5 at recruitment (35 total). Standard rifle
price is 10; Machine Armour's separate armoury price is 50 and is already
included in the Heavy Infantry's 95 ducat option.

Source: [Warbands of Trench Crusade 1.0.2](https://www.trenchcrusade.com/app/uploads/2026/09/Warbands-of-Trench-Crusade.pdf),
printed pages 24–25 (armoury), 27 (Lieutenant), 30 (Yeomen), and 33 (Heavy Infantry).


## Prerequisites (Ubuntu 24.04 / WSL)

Use Bash as your normal Linux user, JDK 21, Maven 3.6.3+, Node.js 24, and
PostgreSQL 16+. Internet access downloads dependencies. From the repository root:

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk-headless maven postgresql postgresql-client git curl rsync openssh-client ca-certificates xz-utils unzip
sudo bash deploy/install-node.sh
```

Java, javac, and Maven must all use Java 21. The Node installer installs the pinned
Node release into `/opt` and links it from `/usr/local/bin`.

## Run and test

```bash
./start.sh
```

This checks tools, installs locked frontend and Maven dependencies, builds the
React page into the Spring JAR, starts a private PostgreSQL instance, applies
Flyway migrations and catalogue data, and starts the app. Open
[http://localhost:8080/](http://localhost:8080/) after Spring reports `Started`.
Keep the terminal open; Ctrl+C stops the app and its private database. Rerunning
preserves data and does not duplicate seed rows. Do not run with sudo.

For Windows if localhost forwarding fails, stop the app and use:

```bash
SERVER_ADDRESS=0.0.0.0 ./start.sh
```

Open the WSL direct address printed by the script. To test, use a second Ubuntu
terminal in the same repository:

```bash
./test.sh
curl -f http://127.0.0.1:8080/api/health
curl -f http://127.0.0.1:8080/api/checkpoint
```

`test.sh` runs React tests and Java HTTP/schema/migration tests against a separate
real PostgreSQL database. Expect React tests to pass, Maven `BUILD SUCCESS`, and
a final `PASS` line. Tests cover catalogue data, seven tables, foreign keys,
nonnegative costs, repeatable migrations, and unavailable-database health behavior.
The V2-to-V3 upgrade test uses an isolated H2 database to verify that existing
roster references survive; endpoint and catalogue tests use PostgreSQL in test.sh.
Plain `mvn verify` (after a frontend build) uses H2 compatibility mode; use
`./test.sh` for the PostgreSQL check.

Local data and generated passwords live in ignored `.local/app/` (port 55432);
tests use `.local/test/` (55433). No `.env`, database account creation, or manual
SQL is required. Keep `.local/` private. `CP1_DB_PORT` and `CP1_TEST_DB_PORT` can
override ports before the respective script starts. `PG_BIN` can specify a
PostgreSQL binary directory. PostgreSQL upgrades require migrating existing data.

## EC2 setup (also rerun when upgrading from the Hello World starter)

Use Ubuntu 24.04 and the instance's **current** public IPv4. The last known address
was [http://34.216.128.112/](http://34.216.128.112/); confirm it in AWS before
using it in your submission. Allow inbound HTTP TCP 80 from
`0.0.0.0/0` and SSH TCP 22 from your IP. PostgreSQL and Spring stay on loopback.

On your **laptop's Ubuntu terminal**, from the repository root:

```bash
EC2_IP=YOUR_CURRENT_PUBLIC_IP
chmod 400 ~/.ssh/cs408.pem
scp -i ~/.ssh/cs408.pem -r deploy ubuntu@"$EC2_IP":~/
ssh -i ~/.ssh/cs408.pem ubuntu@"$EC2_IP"
```

Now **on EC2**, run:

```bash
sudo bash ~/deploy/setup-ec2.sh
sudo nginx -t
sudo systemctl is-enabled nginx postgresql trench-roster
exit
```

Setup installs Java, Maven, Node, PostgreSQL and nginx; creates the app database
and role; stores generated credentials in root-only
`/etc/trench-roster/environment`; and installs the systemd service. Reruns preserve
the credentials and database. The first deployment starts the app. A 502 before
that first deployment is expected.

On your **laptop's Ubuntu terminal**:

```bash
./deploy/deploy.sh -h "$EC2_IP" -i ~/.ssh/cs408.pem
curl -f "http://$EC2_IP/api/health"
```

Deployment runs tests locally, mirrors source with rsync, builds on EC2, replaces
the JAR, restarts systemd, and checks health. Open `http://<PUBLIC-IP>/`. Repeat
for code changes; repeat setup first for service/nginx/runtime changes. PostgreSQL
data stays outside the source mirror and survives deployment and reboot.

Verify automatic startup:

```bash
ssh -i ~/.ssh/cs408.pem ubuntu@"$EC2_IP" 'sudo reboot'
```

After about a minute, load the live page without logging in. A reboot keeps the
IP; stopping/starting the instance can change it. Diagnose server failures with:

```bash
ssh -i ~/.ssh/cs408.pem ubuntu@"$EC2_IP" 'sudo journalctl -u trench-roster -n 60 --no-pager'
```

## Checkpoint submission

Verify a fresh clone and deploy **that same tag** from a separate directory:

```bash
cd ~/projects
git clone --branch cp1 --single-branch https://github.com/Coch8a/CS408NameTBD.git trench-roster-cp1-check
cd trench-roster-cp1-check
./start.sh
# After checking the page, Ctrl+C, then:
./deploy/deploy.sh -h YOUR_CURRENT_PUBLIC_IP -i ~/.ssh/cs408.pem
```

Replace the IP placeholder. The tagged code, live app, post, and video must match.
Leave this version deployed through grading. Check the
[cp1 tag](https://github.com/Coch8a/CS408NameTBD/tree/cp1) in an incognito window.

| Deliverable | Evidence |
| --- | --- |
| Spring backend and React frontend | `pom.xml`, `frontend/src/App.jsx`, live `/` |
| PostgreSQL and initial schema | `src/main/resources/db/migration/`, live `/api/checkpoint` |
| Landing page deployed on EC2 | Live `/` and `/api/health` |
| Setup and deployment scripts | `start.sh`, `deploy/setup-ec2.sh`, `deploy/deploy.sh`, `deploy/trench-roster.service` |
| Setup documentation and EC2 URL | This README and your current Live link |
