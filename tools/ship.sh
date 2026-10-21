#!/usr/bin/env bash
# Commit + push + wait for CI + download the APK. Designed for Termux with git and gh.
# Usage:  bash tools/ship.sh "<conventional commit message>" [vX.Y.Z]
# Passing a tag also publishes a GitHub Release (CI attaches the APK and the changelog section).
set -euo pipefail
msg="${1:?commit message required, e.g. \"feat(watchlist): add silver\"}"
tag="${2:-}"
cd "$(dirname "$0")/.."

# A release keeps versionName in build.gradle.kts equal to the tag, so later builds do not look like older ones.
if [ -n "$tag" ]; then
  ver="${tag#v}"
  sed -i -E "/versionName =/ s/\?: \"[^\"]*\"/?: \"${ver}\"/" app/build.gradle.kts
fi

git add -A
git commit -m "$msg"
git push
if [ -n "$tag" ]; then
  git tag "$tag"
  git push origin "$tag"
fi

sleep 6
run_id="$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')"
echo "Watching run $run_id ..."
if gh run watch "$run_id" --exit-status; then
  rm -rf dist
  gh run download "$run_id" -n TraderLauncher -D dist
  if [ -d "$HOME/storage/downloads" ]; then cp dist/TraderLauncher.apk "$HOME/storage/downloads/"; fi
  echo "OK -> dist/TraderLauncher.apk (also copied to ~/storage/downloads if available)"
else
  echo "BUILD FAILED. Send this output to the AI:"
  echo "  gh run view $run_id --log-failed"
  echo "If only the release step failed, the debug build may still be downloadable:"
  echo "  gh run download $run_id -n TraderLauncher-debug -D dist"
  exit 1
fi
