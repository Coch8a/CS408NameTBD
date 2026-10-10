# Trench Crusade Roster Builder

Current version provides a Spring Boot Hello World page and `/api/health` endpoint. Roster features, React, and PostgreSQL will be added later. No database, sample data, or `.env` file is needed for this version.

## Prerequisites

Use Linux with Bash, JDK 21, Maven 3.6.3+, and Git. Windows users should run the
commands inside Ubuntu through WSL. Deployment also requires SSH, rsync, and curl.
Internet access is required to download dependencies.

Install the tools on Ubuntu 24.04:

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk-headless maven git curl rsync openssh-client ca-certificates
```

`java -version`, `javac -version`, and `mvn -version` must all report Java 21.

## Run locally

```bash
git clone https://github.com/Coch8a/CS408NameTBD.git trench-roster
cd trench-roster
./start.sh
```

The script checks prerequisites, downloads dependencies, builds the application,
and starts it. Open http://localhost:8080 after Spring reports it has started.
Keep the terminal running; press Ctrl+C to stop. The script is safe to rerun.

If Windows cannot reach WSL through localhost, stop the app and run in Ubuntu:

```bash
hostname -I
SERVER_ADDRESS=0.0.0.0 ./start.sh
```

Open `http://<IP-from-hostname>:8080/`. This listening-address override applies
only to that local run.

## Run tests

From the repository root:

```bash
mvn -B -ntp verify
```

Both HTTP tests should pass with `BUILD SUCCESS`. `/api/health` returns HTTP 200
and JSON containing `"status":"UP"`.

## Set up EC2 once

Use the Ubuntu 24.04 instance and its current public IPv4 address. Allow inbound
HTTP TCP 80 from `0.0.0.0/0` and SSH TCP 22 from your current public IP.
Nginx forwards port 80 to Spring on `127.0.0.1:8080`.

Store your key outside the repository at `~/.ssh/cs408.pem`. On your **laptop's
Ubuntu terminal**, from the repository root, replace `YOUR_PUBLIC_IP` and run:

```bash
EC2_IP=YOUR_PUBLIC_IP
chmod 400 ~/.ssh/cs408.pem
scp -i ~/.ssh/cs408.pem -r deploy ubuntu@"$EC2_IP":~/
ssh -i ~/.ssh/cs408.pem ubuntu@"$EC2_IP"
```

Now **on EC2**, run:

```bash
sudo bash ~/deploy/setup-ec2.sh
sudo nginx -t
sudo systemctl is-enabled nginx trench-roster
exit
```

Setup installs Java, Maven, nginx, and deployment tools; creates app directories;
and installs and enables the service. Both services should report `enabled`.
The first deployment starts the app; nginx may return 502 until then.

## Deploy

On your **laptop's Ubuntu terminal**, from the repository root:

```bash
./deploy/deploy.sh -h "$EC2_IP" -i ~/.ssh/cs408.pem
```

Set `EC2_IP` again if using a new terminal. The script runs local tests, copies
source, builds on EC2, restarts the service, and checks the public health endpoint.
After `Deployment succeeded`, open `http://<PUBLIC-IP>/`.

Repeat this command for code updates. For nginx or service-file changes, repeat
the setup step first. Deployment replaces the source mirror; keep persistent
data outside `/opt/trench-roster/source`.

## Verify startup at boot

Connect from your laptop:

```bash
ssh -i ~/.ssh/cs408.pem ubuntu@"$EC2_IP"
```

On EC2:

```bash
sudo systemctl status trench-roster --no-pager
sudo reboot
```

Wait about a minute, then reload the public page without logging in or manually
starting anything. If deployment fails, inspect the logs on EC2:

```bash
sudo journalctl -u trench-roster -n 60 --no-pager
```

## Required files and submission

| File | Purpose |
| --- | --- |
| `start.sh` | Install project dependencies, build, and start locally |
| `deploy/setup-ec2.sh` | Prepare the server; run on EC2 with sudo |
| `deploy/trench-roster.service` | Start at boot and restart after crashes |
| `deploy/deploy.sh` | Test, upload, build, restart, and check health from the laptop |
| `deploy/nginx-trench-roster.conf` | Forward public HTTP requests to Spring |

- Keep the GitHub repository public.
- Keep `*.pem` and `.env` in `.gitignore`; never commit keys or passwords.
- Keep shell scripts in LF format and commit their executable permissions:

  ```bash
  chmod +x start.sh deploy/setup-ec2.sh deploy/deploy.sh
  git update-index --chmod=+x start.sh deploy/setup-ec2.sh deploy/deploy.sh
  ```

- Commit and push changes. Verify a fresh clone starts with `./start.sh` after
  installing only the documented system prerequisites.
- Put your name, public repository URL, and current EC2 public IP in the class spreadsheet.
- Submit `http://<PUBLIC-IP>/` in Canvas after the page works following a reboot.
- Stopping and starting EC2 can change its public IP; rebooting preserves it.
  Update the spreadsheet and submitted URL if the IP changes.
