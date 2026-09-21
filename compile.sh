#!/bin/bash
# Compiles a SafeLang source file (.sl) to Java bytecode.
# Usage: compile.sh <program.sl>

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Argument handling ──────────────────────────────────────────────
if [ $# -ne 1 ]; then
    echo "Usage: $0 <program.sl>" >&2
    exit 1
fi

SL_FILE="$(realpath "$1")"
if [ ! -f "$SL_FILE" ]; then
    echo "ERROR: '$1': file not found" >&2
    exit 1
fi

SL_DIR="$(dirname "$SL_FILE")"
BASENAME="$(basename "$SL_FILE" .sl)"

# Derive a valid Java class name: capitalise each word, drop non-alphanumeric chars.
# e.g. min-01 → Min01 ,  grades → Grades ,  physics → Physics
CLASS_NAME="$(echo "$BASENAME" \
    | sed 's/[^a-zA-Z0-9]/ /g' \
    | awk '{for(i=1;i<=NF;i++) $i=toupper(substr($i,1,1)) substr($i,2); print}' \
    | tr -d ' ')"
[[ "$CLASS_NAME" =~ ^[0-9] ]] && CLASS_NAME="SL${CLASS_NAME}"

JAVA_FILE="$SL_DIR/${CLASS_NAME}.java"

# ── Ensure compiler is built ───────────────────────────────────────
if [ ! -f "$SCRIPT_DIR/src/SafeLangMain.class" ]; then
    echo "Compiler not built — running build.sh first..."
    "$SCRIPT_DIR/build.sh" \
        || { echo "ERROR: build failed" >&2; exit 1; }
fi

# ── Step 1: translate .sl → .java ─────────────────────────────────
echo "[1/2] Translating '$(basename "$SL_FILE")' → '${CLASS_NAME}.java'..."

# Run from $SL_DIR so that 'use "file.sl"' paths resolve relative to the source file.
# Build an explicit absolute classpath (strip the relative '.' from $CLASSPATH and
# prepend the project's src/ and root directories with absolute paths).
JAVA_CP="$SCRIPT_DIR/src:$SCRIPT_DIR:${CLASSPATH#.:}"
cat "$SL_FILE" \
    | (cd "$SL_DIR" && java -ea -cp "$JAVA_CP" SafeLangMain "$CLASS_NAME") \
    > "$JAVA_FILE" \
    || { echo "ERROR: translation failed" >&2; rm -f "$JAVA_FILE"; exit 1; }

# ── Step 2: compile the generated Java ────────────────────────────
echo "[2/2] Compiling '${CLASS_NAME}.java'..."
javac -cp "$SCRIPT_DIR/src" -d "$SL_DIR" "$JAVA_FILE" \
    || { echo "ERROR: Java compilation failed" >&2; exit 1; }

# ── Save last compiled program for run.sh ─────────────────────────
printf '%s\n%s\n' "$SL_DIR" "$CLASS_NAME" > "$SCRIPT_DIR/.last_compiled"

echo ""
echo "Done. Run with:"
echo "   ./run.sh"
