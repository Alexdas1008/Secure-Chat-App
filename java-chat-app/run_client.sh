#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
#  run_client.sh  –  Launch the Swing GUI chat client
#
#  Run as many instances as you like in separate terminals to simulate
#  multiple users connecting to the server.
# ─────────────────────────────────────────────────────────────────────────────

set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/out"

if [ ! -d "$OUT" ]; then
    echo "Output directory not found — running compile.sh first…"
    bash "$ROOT/compile.sh"
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Launching SecureChat Client (Swing GUI)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

cd "$ROOT"
# -Dsun.java2d.uiScale=1 ensures crisp rendering on HiDPI screens
java -Dsun.java2d.uiScale=1 -cp "$OUT" Main
