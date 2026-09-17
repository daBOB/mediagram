#!/usr/bin/env bash
#
# Points git at the tracked hooks in .githooks. Run once per clone.
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
git config core.hooksPath .githooks
echo "hooks enabled: $(git config core.hooksPath)"
echo "pre-push now runs scripts/check.sh; bypass once with 'git push --no-verify'"
