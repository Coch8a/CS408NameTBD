# Trench Crusade Roster Builder

A web application for browsing Trench Crusade units and equipment, with account registration and login. The frontend uses React and Bootstrap, the API uses Spring Boot, and application data is stored in PostgreSQL.

The catalogue currently covers selected New Antioch entries with search, equipment filters, and alphabetical sorting. Roster creation, editing, and saving are planned.

## Progress by checkpoint

### Checkpoint 1 — Application foundation

- Implemented the React frontend, Spring Boot API, and PostgreSQL schema with seven tables.
- Added the New Antioch catalogue, search, filters, and sorting.
- Added startup, testing, EC2 setup, nginx configuration, and systemd deployment files.

### Checkpoint 2 — Accounts and authentication

- Implemented registration with validation, duplicate-account checks, and BCrypt password hashing.
- Added login, session restoration, account details, and logout to the interface.
- Added CSRF protection, session ID rotation, and protected account access.
- Added frontend and backend tests, including the real frontend API client workflow, separate user sessions, and account persistence across application restarts.
- EC2 deployment, live authentication checks, and reboot verification remain required before marking the checkpoint release complete.

### Checkpoint 3 — Roster management

- Planned: create, edit, and save rosters.
- Planned: associate rosters with accounts and enforce ownership.

## Prerequisites

The supported setup is Ubuntu 24.04, either directly or through WSL. Run project scripts in Bash as a normal Linux user. Required tools are JDK 21, Maven 3.6.3 or newer, Node.js 24, and PostgreSQL 16 or newer. Internet access is required to download dependencies.

Install the system tools:

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk-headless maven postgresql postgresql-client git curl rsync openssh-client ca-certificates xz-utils unzip
```

For a fresh checkout:

```bash
git clone https://github.com/Coch8a/CS408NameTBD.git trench-roster
cd trench-roster
```

Install the project's pinned Node.js release from the repository root:

```bash
sudo bash deploy/install-node.sh
```

`java`, `javac`, and Maven must all use Java 21. On Windows, use an Ubuntu terminal and open VS Code through WSL to keep Git, file permissions, and scripts in the same environment.

## Run locally

From the repository root:

```bash
./start.sh
```

The script checks prerequisites, installs project dependencies, builds the frontend and Spring Boot application, starts a private PostgreSQL instance, and applies database migrations and catalogue data automatically. No `.env` file or manual SQL setup is required.

Open [http://localhost:8080/](http://localhost:8080/) after Spring reports `Started`. Keep the terminal open; Ctrl+C stops the application and its private database. Do not run `start.sh` with sudo.

If Windows cannot reach the app through WSL localhost forwarding, restart it with:

```bash
SERVER_ADDRESS=0.0.0.0 ./start.sh
```

Use the WSL address printed by the script. This setting listens on all network interfaces instead of loopback only.

## Accounts and sessions

Use **Register** to create an account, then sign in with its username and password. Usernames accept 3–50 letters, numbers, or underscores. Usernames and email addresses must be unique without regard to capitalization. Passwords require at least 12 characters and must fit within 72 UTF-8 bytes.

Registration does not automatically sign in. After login, the account panel shows the username and email. Refreshing the page restores the active session; signing out invalidates it. Sessions expire after 30 minutes of inactivity. Restarting the application clears sessions, but accounts remain in the database and can sign in again.

The browser manages the HttpOnly, SameSite=Lax session cookie. The frontend does not store passwords or session tokens in browser storage.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/health` | Application and database health. |
| `GET /api/catalog` | Public catalogue data. |
| `GET /api/checkpoint` | Public schema table counts, without account details. |
| `GET /api/auth/csrf` | CSRF token for the current session. |
| `POST /api/auth/register` | Create an account using JSON fields `username`, `email`, `password`, and `confirmPassword`. |
| `POST /api/auth/login` | Sign in using form-encoded `username` and `password`. |
| `GET /api/auth/me` | Current account details; returns 401 when signed out. |
| `POST /api/auth/logout` | Invalidate the session; returns 204 on success. |

API clients must retain the session cookie and send the token from `/api/auth/csrf` in the `X-CSRF-TOKEN` header for registration, login, and logout. Fetch a fresh token after login or logout. The interface handles this automatically.

## Run tests

From the repository root:

```bash
./test.sh
```

The script builds the frontend, runs its tests, and runs Java tests against a separate PostgreSQL test database. Successful runs end with Maven `BUILD SUCCESS` and a `PASS` message.

Coverage includes catalogue data, schema constraints, migration upgrades, registration validation, password hashing, login and logout, CSRF protection, separate user sessions, and account persistence across an application restart. A Node.js workflow calls the actual frontend API functions against a running Java application. Migration upgrade tests use an isolated H2 database.

Plain `mvn verify` after a frontend build uses the H2 fallback unless `TEST_DB_*` variables are provided. Use `./test.sh` for PostgreSQL integration coverage. Automated API tests do not replace browser checks on the deployed site.

With the app running, its public endpoints can also be checked from a second terminal:

```bash
curl -f http://127.0.0.1:8080/api/health
curl -f http://127.0.0.1:8080/api/checkpoint
```

## Catalogue and database updates

The catalogue contains three unit entries and fourteen armoury entries based on [Warbands of Trench Crusade 1.0.2](https://www.trenchcrusade.com/app/uploads/2026/09/Warbands-of-Trench-Crusade.pdf). It is a selected catalogue rather than a complete roster builder. The interface includes cost notes and links to the published rules.

Flyway applies migrations from `src/main/resources/db/migration/` on startup. V1 creates the schema, V2 adds the original sample data, V3 adds the New Antioch catalogue, and V4 adds normalized account identities for registration. Older sample rows remain for referential integrity but are excluded from the public catalogue.

Add a new versioned migration for database changes; do not edit migrations that have already been applied. Preserve existing data when updating the application.

| Local storage | Default port | Purpose |
| --- | --- | --- |
| `.local/app/` | 55432 | Application database and generated credentials. |
| `.local/test/` | 55433 | Separate test database and generated credentials. |

Both directories are ignored by Git. Keep them private and do not delete them to perform an application update. Never commit `.pem` keys or `.env` files. The existing port overrides are `CP1_DB_PORT` and `CP1_TEST_DB_PORT`; `PG_BIN` can select a PostgreSQL binary directory. A PostgreSQL major-version change requires migrating the existing database.

## EC2 setup and deployment

Use an Ubuntu 24.04 instance. Its security group must allow HTTP on TCP port 80 and SSH on TCP port 22 from the administrator's IP address. Spring Boot and PostgreSQL listen on loopback; their ports do not need public access.

In a **local Linux or WSL terminal**, from the repository root, enter the instance's current public IPv4 address or DNS hostname and the absolute path to its private key:

```bash
read -r -p 'EC2 public IP or DNS hostname: ' EC2_HOST
read -r -p 'Absolute path to the private key: ' SSH_KEY
chmod 400 "$SSH_KEY"
scp -i "$SSH_KEY" -r deploy "ubuntu@$EC2_HOST:~/"
ssh -i "$SSH_KEY" "ubuntu@$EC2_HOST"
```

Run setup **on the EC2 server**:

```bash
sudo bash ~/deploy/setup-ec2.sh
sudo nginx -t
sudo systemctl is-enabled nginx postgresql trench-roster
exit
```

Setup installs the runtime tools, PostgreSQL, and nginx; creates the application database; stores generated credentials in `/etc/trench-roster/environment`; and enables the application service on boot. Rerunning setup preserves the database and credentials. The first deployment starts the application; nginx may return 502 before that deployment.

Back in the **local terminal**, deploy:

```bash
./deploy/deploy.sh -h "$EC2_HOST" -i "$SSH_KEY"
curl -f "http://$EC2_HOST/api/health"
```

Deployment runs tests locally, copies source with rsync, builds on EC2, replaces the application JAR, restarts the service, and checks the public health endpoint. Open the instance's public HTTP URL to use the site. Repeat deployment for application changes; rerun setup when changing the runtime, nginx configuration, or systemd service.

Database data and credentials are stored outside the deployed source directory. When HTTPS is configured, set `SESSION_COOKIE_SECURE=true` in the service environment and restart the application.

### Verify deployment and startup on boot

On the live site, register an account, sign in, refresh the page, and sign out. Confirm that invalid credentials are rejected and catalogue search still works.

From the local terminal, reboot the server:

```bash
ssh -i "$SSH_KEY" "ubuntu@$EC2_HOST" 'sudo reboot'
```

Once the server is available, open the live page without opening a new SSH session. Confirm that the application starts automatically and that the same account can sign in again. An application or server restart requires a new login, but must not delete accounts.

Rebooting normally preserves the public IP address; stopping and starting an instance can change an automatically assigned address. Use the current address for deployment and release links.

Inspect service logs if deployment fails:

```bash
ssh -i "$SSH_KEY" "ubuntu@$EC2_HOST" 'sudo journalctl -u trench-roster -n 60 --no-pager'
```

## Checkpoint release verification

Checkpoint tags identify the exact code being reviewed. Keep existing tags unchanged and create the next required tag only after the code and tests are ready. The repository must remain public and accessible while signed out of GitHub.

To verify an existing checkpoint tag from a fresh directory:

```bash
read -r -p 'Checkpoint tag to verify (for example, cp2): ' CHECKPOINT
git clone --branch "$CHECKPOINT" --single-branch https://github.com/Coch8a/CS408NameTBD.git "trench-roster-$CHECKPOINT-check"
cd "trench-roster-$CHECKPOINT-check"
./test.sh
./start.sh
```

After checking the application, stop it with Ctrl+C and deploy from that same tagged checkout using the deployment command above. The tagged source, live application, progress claims, and demonstration must match. Complete live authentication and reboot checks before marking deployment as verified.
