#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

if [[ -f .env.local ]]; then
  set -a
  source .env.local
  set +a
fi

if [[ -x "$repo_root/.tools/zulu17.54.21-ca-jdk17.0.13-macosx_aarch64/zulu-17.jdk/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="$repo_root/.tools/zulu17.54.21-ca-jdk17.0.13-macosx_aarch64/zulu-17.jdk/Contents/Home"
  export PATH="$repo_root/.tools/apache-maven-3.9.9/bin:$JAVA_HOME/bin:$repo_root/.tools/node-v22.14.0-darwin-arm64/bin:$PATH"
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn not found. Set JAVA_HOME/PATH to Java 17 + Maven 3.9 first." >&2
  exit 1
fi
if ! command -v npm >/dev/null 2>&1; then
  echo "npm not found. Install Node.js 22+ first." >&2
  exit 1
fi

if [[ ! -d node_modules ]]; then
  npm install
fi

# 某些 IDE/终端会设置 ELECTRON_RUN_AS_NODE，导致 Electron 以 Node 模式启动并报 app 不存在。
unset ELECTRON_RUN_AS_NODE || true

# main.ts 加载 VITE_DEV_SERVER_URL；Electron 主进程会自动启动 Spring Boot。
npm --workspace apps/desktop run build:electron
npm --workspace apps/desktop run dev -- --host 127.0.0.1 --port 5173 --strictPort &
vite_pid=$!
cleanup() { kill "$vite_pid" 2>/dev/null || true; }
trap cleanup EXIT INT TERM

for _ in {1..60}; do
  if curl -fsS http://127.0.0.1:5173 >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

if ! curl -fsS http://127.0.0.1:5173 >/dev/null 2>&1; then
  echo "Vite dev server did not start on 127.0.0.1:5173." >&2
  exit 1
fi

VITE_DEV_SERVER_URL=http://127.0.0.1:5173 ./node_modules/.bin/electron apps/desktop
