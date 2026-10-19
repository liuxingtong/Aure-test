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

# 只保留后端实际需要的 JDK 模块，避免把完整 JDK 的 jmods/demo/man/include 打进应用。
MODULES="java.base,java.logging,java.naming,java.management,java.xml,java.instrument,jdk.unsupported,java.net.http,jdk.crypto.ec,jdk.zipfs,jdk.localedata,java.desktop,java.sql,jdk.naming.dns,jdk.charsets,java.security.jgss,java.security.sasl"
if [[ "$JRE_SOURCE" != "$ROOT/runtime/jre/mac-arm64" && -x "$JRE_SOURCE/bin/jlink" ]]; then
  rm -rf "$ROOT/runtime/jre/mac-arm64"
  "$JRE_SOURCE/bin/jlink" --add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages --compress=2 \
    --output "$ROOT/runtime/jre/mac-arm64"
elif [[ "$JRE_SOURCE" != "$ROOT/runtime/jre/mac-arm64" ]]; then
  rm -rf "$ROOT/runtime/jre/mac-arm64"
  cp -R "$JRE_SOURCE" "$ROOT/runtime/jre/mac-arm64"
fi
chmod +x "$ROOT/runtime/jre/mac-arm64/bin/java"
printf '{"platform":"darwin","arch":"arm64"}\n' > "$ROOT/runtime/jre/mac-arm64/runtime.json"
echo "Prepared macOS arm64 backend and minimal Java runtime under $ROOT/runtime"
