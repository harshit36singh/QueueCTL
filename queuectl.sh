#!/usr/bin/env bash
# Wrapper so you can run `./queuectl.sh <command>` instead of the full java -jar invocation.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/target/queuectl.jar"

if [ ! -f "$JAR" ]; then
    echo "queuectl.jar not found at $JAR. Build it first with: mvn package" >&2
    exit 1
fi

exec java -jar "$JAR" "$@"
