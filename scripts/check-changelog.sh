#!/usr/bin/env bash
set -euo pipefail

root=$(git rev-parse --show-toplevel)
cd "$root"

changed=$( {
  git diff --name-only
  git diff --cached --name-only
  git ls-files --others --exclude-standard
} | sort -u )

source_changed=0
while IFS= read -r path; do
  case "$path" in
    app/*|build.gradle.kts|settings.gradle.kts|gradle.properties|gradle/*|*.gradle|*.gradle.kts)
      source_changed=1
      ;;
  esac
done <<< "$changed"

if [[ "$source_changed" == 1 ]] && ! grep -q '^## \[Unreleased\]' CHANGELOG.md; then
  echo "source changes require an [Unreleased] CHANGELOG section" >&2
  exit 1
fi

echo "changelog check passed"
