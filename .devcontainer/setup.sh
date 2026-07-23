#!/usr/bin/env bash
set -euo pipefail

echo "==> Node.js: installing Claude Code and markdownlint-cli2"
npm install -g --silent @anthropic-ai/claude-code markdownlint-cli2

echo "==> Setup complete"