#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLATFORM="${1:-darwin}"
ARCH="${2:-arm64}"
if [[ "$PLATFORM/$ARCH" != "darwin/arm64" ]]; then
  echo "This script prepares macOS arm64. Use prepare-backend-runtime.ps1 for Windows x64." >&2
  exit 1
fi

JRE_SOURCE="${QA_JRE_SOURCE:-${JAVA_HOME:-}}"
if [[ -z "$JRE_SOURCE" || ! -x "$JRE_SOURCE/bin/java" ]]; then
  echo 'Set QA_JRE_SOURCE or JAVA_HOME to a Java 17 runtime before packaging.' >&2
  exit 1
fi

export JAVA_HOME="$JRE_SOURCE"
export PATH="$ROOT/.tools/apache-maven-3.9.9/bin:$JRE_SOURCE/bin:$PATH"
mvn -q -f "$ROOT/apps/backend/pom.xml" -DskipTests package

mkdir -p "$ROOT/runtime/backend" "$ROOT/runtime/jre/mac-arm64"
cp "$ROOT/apps/backend/target/qa-backend-0.1.0.jar" "$ROOT/runtime/backend/qa-backend-0.1.0.jar"
if [[ "$JRE_SOURCE" != "$ROOT/runtime/jre/mac-arm64" ]]; then
  rm -rf "$ROOT/runtime/jre/mac-arm64"
  cp -R "$JRE_SOURCE" "$ROOT/runtime/jre/mac-arm64"
fi
chmod +x "$ROOT/runtime/jre/mac-arm64/bin/java"
printf '{"platform":"darwin","arch":"arm64"}\n' > "$ROOT/runtime/jre/mac-arm64/runtime.json"
echo "Prepared macOS arm64 backend and Java runtime under $ROOT/runtime"
