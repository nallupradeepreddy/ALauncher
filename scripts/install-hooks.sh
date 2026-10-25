#!/bin/sh
# Configures git hooks strictly and exclusively for this repository.
# Hooks are installed into .git/hooks with local-only scope, never modifying global git configuration.

set -e

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
RAW_GIT_DIR="$(git rev-parse --git-dir 2>/dev/null || echo "$ROOT_DIR/.git")"
case "$RAW_GIT_DIR" in
    /*) GIT_DIR="$RAW_GIT_DIR" ;;
    *) GIT_DIR="$ROOT_DIR/$RAW_GIT_DIR" ;;
esac

if [ ! -d "$GIT_DIR/hooks" ]; then
    mkdir -p "$GIT_DIR/hooks"
fi

# Ensure tracked source hook is executable
chmod +x "$ROOT_DIR/.githooks/pre-commit"

# Install pre-commit hook directly into this repository's .git/hooks/
cp "$ROOT_DIR/.githooks/pre-commit" "$GIT_DIR/hooks/pre-commit"
chmod +x "$GIT_DIR/hooks/pre-commit"

# Explicitly configure --local scope (writes to .git/config only, never ~/.gitconfig)
git config --local core.hooksPath "$GIT_DIR/hooks"

echo "✅ ALauncher git hooks installed locally for this repository only:"
echo "   Location: $GIT_DIR/hooks/pre-commit"
