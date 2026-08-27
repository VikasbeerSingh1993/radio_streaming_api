#!/usr/bin/env bash
# Per-boot reconciliation: bring up the local datastores and point the app at them.
# Must be idempotent, avoid duplicate processes, and return once the stores are ready.
# The application itself runs in the "radio-api" terminal (see run-app.sh).
set -euo pipefail

echo "==> Starting MongoDB"
if ! pgrep -x mongod >/dev/null 2>&1; then
  sudo mkdir -p /var/lib/mongodb /var/log/mongodb
  sudo chown -R mongodb:mongodb /var/lib/mongodb /var/log/mongodb
  sudo -u mongodb mongod --dbpath /var/lib/mongodb --bind_ip 127.0.0.1 --port 27017 \
    --fork --logpath /var/log/mongodb/mongod.log
fi

echo "==> Starting MySQL"
mysql_up() {
  for _ in $(seq 1 30); do
    if sudo mysqladmin ping >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  return 1
}

sudo service mysql start 2>/dev/null || true
if ! mysql_up; then
  # A snapshot can capture an unclean MySQL datadir that InnoDB cannot recover on
  # overlayfs. For a throwaway dev environment it is safe to re-initialize it.
  echo "   MySQL did not come up; re-initializing a fresh datadir"
  sudo service mysql stop 2>/dev/null || true
  sudo mv /var/lib/mysql "/var/lib/mysql.broken.$(date +%s)" 2>/dev/null || sudo rm -rf /var/lib/mysql
  sudo mkdir -p /var/lib/mysql
  sudo chown mysql:mysql /var/lib/mysql
  sudo mysqld --initialize-insecure --user=mysql --datadir=/var/lib/mysql
  sudo service mysql start 2>/dev/null || true
  mysql_up || { echo "   ERROR: MySQL still not reachable"; exit 1; }
fi

echo "==> Ensuring local MySQL databases and dev user"
# The SaaS/CMS/Gurbani layer needs MySQL. These are throwaway local dev credentials.
sudo mysql <<'SQL'
CREATE DATABASE IF NOT EXISTS bani_search CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS divine_bliss_web CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'radiodev'@'localhost' IDENTIFIED BY 'radiodevpass';
CREATE USER IF NOT EXISTS 'radiodev'@'127.0.0.1' IDENTIFIED BY 'radiodevpass';
CREATE USER IF NOT EXISTS 'radiodev'@'%' IDENTIFIED BY 'radiodevpass';
GRANT ALL PRIVILEGES ON *.* TO 'radiodev'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'radiodev'@'127.0.0.1' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'radiodev'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL

# The app rewrites loopback MySQL hosts (127.0.0.1/localhost) to a hardcoded remote
# server (AppCredentialsReader#resolveMysql). Use a non-loopback alias that maps to
# 127.0.0.1 so the local MySQL is used without touching that remote host.
echo "==> Ensuring 'mysql-local' hosts alias"
grep -q 'mysql-local' /etc/hosts || echo '127.0.0.1 mysql-local' | sudo tee -a /etc/hosts

echo "==> Seeding MySQL connection into Mongo app_credentials (read at app startup)"
# Plaintext password is fine: CredentialCrypto#decrypt returns non-'enc:' values as-is.
mongosh --quiet divine_bliss_streaming --eval '
db.app_credentials.updateOne(
  { type: "MYSQL" },
  { $set: { type: "MYSQL", fields: { host: "mysql-local", port: "3306", username: "radiodev", password: "radiodevpass", database: "bani_search", useSsl: "false" }, updatedAt: new Date() } },
  { upsert: true });'

echo "==> Datastores ready."
