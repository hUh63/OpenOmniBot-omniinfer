#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

chmod +x .githooks/pre-commit
git config --local core.hooksPath .githooks

echo "Git hooks installed."
echo "pre-commit hook path: $(git config --local core.hooksPath)"
