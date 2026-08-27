#!/usr/bin/env bash
# Runs the Radio Streaming API against the local datastores brought up by start.sh.
# Kept in a terminal so its logs stay visible and it can be restarted easily.
set -euo pipefail

cd "$(dirname "$0")/.."

# Local bootstrap Mongo; JWT secret also seeds the credential-encryption key.
export MONGODB_URI="${MONGODB_URI:-mongodb://localhost:27017/divine_bliss_streaming}"
export JWT_SECRET="${JWT_SECRET:-local-dev-jwt-secret-please-change-0123456789abcdef}"

JAR="target/radio-streaming-api-1.0.0.jar"
if [ ! -f "$JAR" ]; then
  echo "==> Jar missing, building"
  mvn -q -DskipTests package
fi

echo "==> Starting Radio Streaming API on http://localhost:8080"
exec java -jar "$JAR" --server.port="${PORT:-8080}"
