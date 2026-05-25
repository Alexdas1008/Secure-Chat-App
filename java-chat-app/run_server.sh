#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
#  run_server.sh  –  Start the chat server
#
#  Optional arguments:
#    $1  port      (default 9000)
#    $2  serverId  (default SERVER-1)
#
#  Examples:
#    ./run_server.sh                   # port 9000, id SERVER-1
#    ./run_server.sh 9001 SERVER-2     # second node on port 9001
# ─────────────────────────────────────────────────────────────────────────────

set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/out"

if [ ! -d "$OUT" ]; then
    echo "Output directory not found — running compile.sh first…"
    bash "$ROOT/compile.sh"
fi

PORT="${1:-9000}"
SERVER_ID="${2:-SERVER-1}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Starting Chat Server"
echo "  Server ID : $SERVER_ID"
echo "  Port      : $PORT"
echo "  Press Ctrl+C to stop"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

cd "$ROOT"
java -cp "$OUT" Main server "$PORT" "$SERVER_ID"
