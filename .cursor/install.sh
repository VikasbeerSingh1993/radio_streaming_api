#!/usr/bin/env bash
# Idempotent repository setup for the Radio Streaming API Cloud Agent environment.
# Installs the toolchain (Maven) and the two local datastores the app needs
# (MongoDB for the radio catalog, MySQL for the SaaS/CMS/Gurbani layer), then
# builds the Spring Boot jar. Per-boot service startup lives in start.sh.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

CODENAME="$(. /etc/os-release && echo "${VERSION_CODENAME:-noble}")"

echo "==> Installing system packages (Maven, MySQL, tooling)"
sudo apt-get update -y
sudo apt-get install -y --no-install-recommends \
  maven mysql-server ca-certificates curl gnupg

echo "==> Installing MongoDB 8.0 (not in the default Ubuntu repositories)"
if ! command -v mongod >/dev/null 2>&1; then
  curl -fsSL https://www.mongodb.org/static/pgp/server-8.0.asc \
    | sudo gpg -o /usr/share/keyrings/mongodb-server-8.0.gpg --dearmor --yes
  echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu ${CODENAME}/mongodb-org/8.0 multiverse" \
    | sudo tee /etc/apt/sources.list.d/mongodb-org-8.0.list
  sudo apt-get update -y
  sudo apt-get install -y mongodb-org
fi

echo "==> Preparing local MongoDB data directory"
sudo mkdir -p /var/lib/mongodb /var/log/mongodb
sudo chown -R mongodb:mongodb /var/lib/mongodb /var/log/mongodb

echo "==> Building the application (dependencies + jar). Tests run in CI via 'mvn test'."
mvn -B -DskipTests package

# Leave MySQL/MongoDB stopped so a build snapshot never captures a live (and, on
# overlayfs, unrecoverable) datadir. start.sh brings the services up per boot.
echo "==> Stopping datastores so the snapshot captures a clean state"
sudo service mysql stop 2>/dev/null || true

echo "==> Install complete."
