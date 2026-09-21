#!/bin/bash
# Compiles the SafeLang compiler toolchain (ANTLR4 grammar + Java sources).
# Must be run from the project root.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Building SafeLang compiler..."
(cd "$SCRIPT_DIR" && antlr4-build) \
    || { echo "ERROR: build failed" >&2; exit 1; }
javac -d "$SCRIPT_DIR/src" "$SCRIPT_DIR/src/Fraction.java" \
    || { echo "ERROR: Fraction compilation failed" >&2; exit 1; }
echo "Done."
