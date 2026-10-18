#!/usr/bin/env bash
set -euo pipefail

if [[ -f .env.local ]]; then
  set -a
  source .env.local
  set +a
fi

echo "Start backend with: mvn -f apps/backend spring-boot:run"
echo "Start desktop with: npm run dev:desktop"
