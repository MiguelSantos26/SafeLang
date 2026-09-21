#!/bin/bash
# Runs the last compiled SafeLang program.
# Usage: run.sh [program.sl | ClassName]
#   No argument  — runs the program recorded by the last compile.sh invocation.
#   program.sl   — derives the class name from the given source file path.
#   ClassName    — runs the given class, looking for .class in the same directory
#                  as the last compiled program (or current directory as fallback).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LAST="$SCRIPT_DIR/.last_compiled"

# Helper: derive Java class name from a .sl filename (same logic as compile.sh)
derive_class() {
    local basename
    basename="$(basename "$1" .sl)"
    local name
    name="$(echo "$basename" \
        | sed 's/[^a-zA-Z0-9]/ /g' \
        | awk '{for(i=1;i<=NF;i++) $i=toupper(substr($i,1,1)) substr($i,2); print}' \
        | tr -d ' ')"
    [[ "$name" =~ ^[0-9] ]] && name="SL${name}"
    echo "$name"
}

if [ $# -eq 0 ]; then
    # ── No argument: use last compiled ────────────────────────────
    if [ ! -f "$LAST" ]; then
        echo "ERROR: no compiled program found — run compile.sh first." >&2
        exit 1
    fi
    CLASS_DIR="$(sed -n '1p' "$LAST")"
    CLASS_NAME="$(sed -n '2p' "$LAST")"

elif [[ "$1" == *.sl ]]; then
    # ── .sl file path given ────────────────────────────────────────
    SL_FILE="$(realpath "$1")"
    CLASS_DIR="$(dirname "$SL_FILE")"
    CLASS_NAME="$(derive_class "$SL_FILE")"

else
    # ── Class name given directly ──────────────────────────────────
    CLASS_NAME="$1"
    if [ -f "$LAST" ]; then
        CLASS_DIR="$(sed -n '1p' "$LAST")"
    else
        CLASS_DIR="."
    fi
fi

if [ ! -f "$CLASS_DIR/$CLASS_NAME.class" ]; then
    echo "ERROR: '$CLASS_DIR/$CLASS_NAME.class' not found — run compile.sh first." >&2
    exit 1
fi

exec java -cp "$CLASS_DIR:$SCRIPT_DIR/src" "$CLASS_NAME"
