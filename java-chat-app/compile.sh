#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
#  compile.sh  –  Compile the Distributed Encrypted Chat Application
# ─────────────────────────────────────────────────────────────────────────────

set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src"
OUT="$ROOT/out"
MAIN="$ROOT/Main.java"

# Clean and recreate output directory
rm -rf "$OUT"
mkdir -p "$OUT"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Compiling Distributed Encrypted Chat Application"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Collect every .java source file
SOURCES=$(find "$SRC" -name "*.java") 
SOURCES="$MAIN $SOURCES"

echo "Source files found:"
echo "$SOURCES" | sed 's|'"$ROOT"'/||g' | sort | sed 's/^/  /'
echo ""

# Compile with UTF-8 encoding; -Xlint:unchecked for educational value
javac -encoding UTF-8 -d "$OUT" -sourcepath "$SRC" $SOURCES

echo ""
echo "✓ Compilation successful!"
echo "  Class files written to: out/"
echo ""
echo "Next steps:"
echo "  Start server  → ./run_server.sh"
echo "  Start client  → ./run_client.sh"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
