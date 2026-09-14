#!/usr/bin/env bash
# Compatibility entry point for the shared source/documentation gate.
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec python3 "$PROJECT_DIR/scripts/check_repository.py"
