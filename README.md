# Trench Crusade Roster Builder

A web application for browsing Trench Crusade units and equipment and managing saved rosters. Built with React, Bootstrap, Spring Boot, and PostgreSQL.

The catalogue includes selected New Antioch units and equipment with search, equipment filters, and alphabetical sorting. Signed-in users can create, open, edit, and delete their rosters, then add or remove individual units.

## Progress by checkpoint

### Checkpoint 1 — Application foundation

- React frontend, Spring Boot API, and PostgreSQL schema with seven tables.
- New Antioch catalogue with search, filters, and sorting.
- Startup, testing, EC2 setup, nginx configuration, and systemd deployment scripts.

### Checkpoint 2 — Accounts and authentication

- Registration with validation, duplicate-account checks, and BCrypt password hashing.
- Login, session restoration, account details, and logout.
- CSRF protection, session ID rotation, and protected account access.
- Tests for registration, authentication, separate sessions, and account persistence after restart.

### Checkpoint 3 — Roster management

- Saved roster dashboard with creation, viewing, settings updates, and deletion.
- Roster names, faction selection, and ducat limits.
- Searchable unit selection with separate entries for each added unit.
- Unit removal, automatic unit saving, and updated dashboard counts.
- Backend ownership checks for all roster and unit operations.
- Tests for access permissions, duplicate units, failed requests, migration safety, and roster persistence after restart.


## Prerequisites

Use Ubuntu 24.04 or Ubuntu through WSL. Run scripts in Bash as a normal Linux user. Required tools are JDK 21, Maven 3.6.3+, Node.js 24, and PostgreSQL 16+. Internet access is required to download dependencies.

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk-headless maven postgresql postgresql-client git curl rsync openssh-client ca-certificates xz-utils unzip

git clone https://github.com/Coch8a/CS408NameTBD.git trench-roster
cd trench-roster
sudo bash deploy/install-node.sh
```

`java`, `javac`, and Maven must use Java 21. On Windows, use an Ubuntu terminal and open VS Code through WSL.

## Run locally

From the repository root:

```bash
./start.sh
```

The script checks prerequisites, installs dependencies, builds the application, starts a private PostgreSQL instance, and applies database migrations. No `.env` file or manual SQL setup is required.

Open [http://localhost:8080/](http://localhost:8080/) after Spring reports `Started`. Keep the terminal open. Ctrl+C stops the application and its private database. Do not run the script with sudo.

If Windows cannot reach the app through WSL localhost forwarding, restart it with:

```bash
SERVER_ADDRESS=0.0.0.0 ./start.sh
```

Open the WSL address printed by the script. This setting listens on all network interfaces.

## Accounts and rosters

Register an account, then sign in. Usernames accept 3–50 letters, numbers, or underscores. Usernames and email addresses must be unique regardless of capitalization. Passwords require at least 12 characters and must fit within 72 UTF-8 bytes.

Use **New roster** to enter a name, faction, and ducat limit. Open a saved roster to edit its settings or manage its units. Each addition creates a separate unit entry, so removing one Yeoman leaves the others intact. Unit changes save automatically. Deleting a roster removes its units.

Each account can access only its own rosters. The backend checks ownership on every roster and unit request. Signing out removes private roster information from the page.

Refreshing the page restores an active session. Sessions expire after 30 minutes of inactivity. Restarting the application requires a new login; accounts, rosters, and units remain saved. Session cookies use HttpOnly and SameSite=Lax. The frontend does not store passwords or session tokens in browser storage.

## API

| Endpoint | Purpose |
| --- | --- |
| `GET /api/health` | Application and database health. |
| `GET /api/catalog` | Public catalogue. |
| `GET /api/checkpoint` | Schema table counts without account details. |
| `GET /api/auth/csrf` | CSRF token. |
| `POST /api/auth/register` | Register with JSON fields `username`, `email`, `password`, and `confirmPassword`. |
| `POST /api/auth/login` | Sign in with form-encoded `username` and `password`. |
| `GET /api/auth/me` | Current account; 401 when signed out. |
| `POST /api/auth/logout` | End the session. |
| `GET /api/rosters` | List the current account's rosters. |
| `GET /api/rosters/factions` | List supported roster factions. |
| `POST /api/rosters` | Create with JSON fields `rosterName`, `factionId`, and `ducatLimit`. |
| `GET /api/rosters/{id}` | Open an owned roster. |
| `PUT /api/rosters/{id}` | Update the same three roster settings. |
| `DELETE /api/rosters/{id}` | Delete an owned roster. |
| `GET /api/rosters/{id}/units` | Roster details, saved unit entries, and available units. |
| `POST /api/rosters/{id}/units` | Add one unit with JSON field `unitId`. |
| `DELETE /api/rosters/{id}/units/{entryId}` | Remove one saved entry using its entry ID. |

Roster endpoints require login. API clients must retain cookies and send a CSRF token in `X-CSRF-TOKEN` for POST, PUT, and DELETE requests, including registration, login, and logout. Fetch a fresh token after login or logout. The interface handles this automatically. Missing rosters and rosters owned by another account return 404.

## Run tests

```bash
./test.sh
```

The script builds the frontend, runs React tests, and runs Java tests against a separate PostgreSQL database. A successful run ends with Maven `BUILD SUCCESS` and a `PASS` message.

Tests cover catalogue data, schema constraints, registration, authentication, CSRF protection, roster CRUD, ownership checks, unit selection, and saved data after application restart. Unit tests also check duplicate removal, failed requests, dashboard counts, and concurrent additions. Migration tests use isolated H2 databases to check upgrades without losing roster references or equipment.

Plain `mvn verify` after a frontend build uses H2 unless `TEST_DB_*` variables are set. Use `./test.sh` for PostgreSQL integration tests.

With the application running, check its health from another terminal:

```bash
curl -f http://127.0.0.1:8080/api/health
```

## Catalogue and database

The catalogue contains three unit entries and fourteen armoury entries based on [Warbands of Trench Crusade 1.0.2](https://www.trenchcrusade.com/app/uploads/2026/09/Warbands-of-Trench-Crusade.pdf). It covers selected entries, not the full faction. Prices and armour options are catalogue references; equipment selection and roster totals are not implemented.

Flyway applies migrations at startup:

- V1 creates the schema.
- V2 adds the original sample catalogue.
- V3 adds the New Antioch catalogue.
- V4 adds normalized account identities.
- V5 splits grouped roster units into individual entries and preserves existing equipment assignments.

SQL migrations are in `src/main/resources/db/migration/`. V5 is a Java migration in `src/main/java/db/migration/`. Add a new version for database changes; do not edit applied migrations. Older sample catalogue rows remain for existing references but are excluded from the public catalogue.

| Local storage | Default port | Purpose |
| --- | --- | --- |
| `.local/app/` | 55432 | Application database and generated credentials. |
| `.local/test/` | 55433 | Test database and generated credentials. |

Keep these ignored directories private. Do not delete them when updating the application. Never commit `.pem` keys or `.env` files. Use `CP1_DB_PORT` and `CP1_TEST_DB_PORT` to override ports, or `PG_BIN` to select a PostgreSQL binary directory. PostgreSQL major-version upgrades require migrating the database.

## EC2 setup and deployment

Use Ubuntu 24.04. Allow inbound HTTP on TCP port 80 and SSH on TCP port 22 from the administrator's IP. Spring Boot and PostgreSQL stay on loopback.

From the repository root in a local Linux or WSL terminal:

```bash
read -r -p 'EC2 public IP or DNS hostname: ' EC2_HOST
read -r -p 'Absolute path to the private key: ' SSH_KEY
chmod 400 "$SSH_KEY"
scp -i "$SSH_KEY" -r deploy "ubuntu@$EC2_HOST:~/"
ssh -i "$SSH_KEY" "ubuntu@$EC2_HOST"
```

On EC2:

```bash
sudo bash ~/deploy/setup-ec2.sh
sudo nginx -t
sudo systemctl is-enabled nginx postgresql trench-roster
exit
```

Setup installs runtime tools, PostgreSQL, and nginx; creates the database; stores credentials in `/etc/trench-roster/environment`; and enables the application service on boot. Rerunning setup preserves data and credentials. The first deployment starts the application.

Back in the same local terminal:

```bash
./deploy/deploy.sh -h "$EC2_HOST" -i "$SSH_KEY"
curl -f "http://$EC2_HOST/api/health"
```

Deployment runs tests, copies source, builds on EC2, replaces the JAR, restarts the service, and checks health. Open `http://<EC2_HOST>/` using the instance's current address. Repeat deployment for code changes; rerun setup for runtime, nginx, or service changes. Database data stays outside the source directory.

For service logs:

```bash
ssh -i "$SSH_KEY" "ubuntu@$EC2_HOST" 'sudo journalctl -u trench-roster -n 60 --no-pager'
```

When HTTPS is configured, set `SESSION_COOKIE_SECURE=true` in the service environment and restart the application.
