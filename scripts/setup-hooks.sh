#!/usr/bin/env bash
# Wire .githooks/ as this clone's hooks directory.
#
# Run once after `git clone`. Idempotent — safe to re-run. Each fresh clone
# needs this because core.hooksPath is local git config, not committed. In a
# bare-clone worktree layout the setting is stored on the shared bare config
# and applies to every worktree at once, so once-per-clone is enough either way.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

git config core.hooksPath .githooks

# Make sure the hook scripts are executable in case the user's umask or filesystem
# stripped the bit.
chmod +x .githooks/* 2>/dev/null || true

echo "core.hooksPath -> $(git config --get core.hooksPath)"
echo "Hooks installed:"
ls -1 .githooks
echo
echo "Pre-commit and pre-push will now run automatically. To bypass once: git commit --no-verify or git push --no-verify."
