#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

if [[ -x "$repo_root/.tools/zulu17.54.21-ca-jdk17.0.13-macosx_aarch64/zulu-17.jdk/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="$repo_root/.tools/zulu17.54.21-ca-jdk17.0.13-macosx_aarch64/zulu-17.jdk/Contents/Home"
  export PATH="$repo_root/.tools/apache-maven-3.9.9/bin:$JAVA_HOME/bin:$repo_root/.tools/node-v22.14.0-darwin-arm64/bin:$PATH"
fi

echo '[1/4] backend tests'
mvn -q -f apps/backend/pom.xml test
if [[ -f .env.local ]]; then
  set -a
  source .env.local
  set +a
fi
# Verification uses the deterministic demo provider; external AI credentials are never exercised by CI checks.
export AI_ENABLED=false
mvn -q -f apps/backend/pom.xml spring-boot:run > /tmp/qa-backend-verify.log 2>&1 &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true' EXIT
for attempt in {1..30}; do
  if curl -fsS http://127.0.0.1:8080/api/v1/health >/dev/null; then break; fi
  sleep 1
done
echo '[2/4] frontend component tests'
npm --workspace apps/desktop run test
echo '[3/4] desktop production build'
npm run build:desktop
echo '[4/4] electron ipc integration'
npm run verify:ipc
echo 'all verification checks passed'
