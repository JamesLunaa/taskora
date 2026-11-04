#!/usr/bin/env bash
# Builds the signed Android APK locally and publishes it as a GitHub release (tag v<version>).
# The signing key never leaves this machine. The desktop app keeps its own workflow.
#
# Usage: scripts/release.sh [--dry-run] <version>      e.g. scripts/release.sh 1.3.0
#
# Before running: bump versionCode and versionName in app/build.gradle.kts, move the
# changes from "## [Unreleased]" to "## [<version>] - <date>" in CHANGELOG.md, merge to
# master, and check out master.
set -euo pipefail

dry_run=false
if [[ "${1:-}" == "--dry-run" ]]; then dry_run=true; shift; fi
version="${1:-}"
[[ -n "$version" ]] || { echo "Usage: scripts/release.sh [--dry-run] <version>" >&2; exit 1; }
tag="v$version"

cd "$(git rev-parse --show-toplevel)"
fail() { echo "error: $*" >&2; exit 1; }
warn() { echo "warning: $*" >&2; }

# Repo state: clean master, in sync with GitHub.
[[ "$(git branch --show-current)" == "master" ]] || fail "check out master first"
[[ -z "$(git status --porcelain)" ]] || fail "working tree has uncommitted changes"
git fetch -q origin master --tags
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/master)" ]] || fail "master differs from origin/master; push or pull first"
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  $dry_run && warn "tag $tag already exists" || fail "tag $tag already exists"
fi

# Version, versionCode and changelog.
gradle_file=app/build.gradle.kts
version_name() { sed -n 's/^ *versionName = "\(.*\)"$/\1/p'; }
version_code() { sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p'; }
app_version=$(version_name < "$gradle_file")
[[ "$app_version" == "$version" ]] || fail "versionName in $gradle_file is $app_version, not $version"
code=$(version_code < "$gradle_file")
last_tag=$(git tag -l 'v*' --sort=-v:refname | grep -vx "$tag" | head -1 || true)
if [[ -n "$last_tag" ]]; then
  last_code=$(git show "$last_tag:$gradle_file" | version_code)
  (( code > last_code )) || fail "versionCode is $code but $last_tag already used $last_code; bump it"
fi
grep -q "^## \[$version\]" CHANGELOG.md || fail "CHANGELOG.md has no '## [$version]' section"
unreleased=$(awk '/^## \[Unreleased\]/ { on = 1; next } on && /^## \[/ { exit } on && NF { print }' CHANGELOG.md)
[[ -z "$unreleased" ]] || warn "CHANGELOG.md still has entries under [Unreleased]; they won't be in this release"
[[ -f keystore.properties ]] || fail "keystore.properties is missing, so the APK can't be signed"

# Build tools from the Android SDK.
sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)}"
build_tools=$(ls -d "$sdk"/build-tools/* 2>/dev/null | sort -V | tail -1)
[[ -n "$build_tools" ]] || fail "Android SDK build-tools not found (set ANDROID_HOME)"

echo "==> Testing and building Taskora $version (versionCode $code)"
./gradlew -q test assembleRelease

# An unsigned build would be named taskora-release-unsigned.apk.
apk_out=app/build/outputs/apk/release/taskora-release.apk
[[ -f "$apk_out" ]] || fail "no signed APK at $apk_out (is keystore.properties correct?)"
"$build_tools/apksigner" verify "$apk_out" || fail "APK signature check failed"
built_version=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <("$build_tools/aapt2" dump badging "$apk_out" | head -1))
[[ "$built_version" == "$version" ]] || fail "built APK reports version $built_version"

# A dry run leaves dist/ alone, so it always matches what was published.
if $dry_run; then out_dir=$(mktemp -d); else out_dir=dist/$version; mkdir -p "$out_dir"; fi
apk_name="taskora-$version.apk"
cp "$apk_out" "$out_dir/$apk_name"
(cd "$out_dir" && sha256sum "$apk_name" > SHA256SUMS)
sha=$(cut -d' ' -f1 "$out_dir/SHA256SUMS")

# Release notes: this version's changelog section, plus install info.
notes=$(mktemp)
trap 'rm -f "$notes"' EXIT
# $(...) drops trailing blank lines, so exactly one blank line precedes "### Install".
printf '%s\n' "$(awk -v v="$version" '
  $0 ~ "^## \\[" v "\\]" { on = 1; next }
  on && /^## \[/ { exit }
  on && /^\[[^]]+\]: / { next }
  on { print }
' CHANGELOG.md | sed -e '/./,$!d')" > "$notes"
cat >> "$notes" <<NOTES

### Install

Android app. Install \`$apk_name\` on your phone; it updates an existing install and keeps your tasks.

SHA-256: \`$sha\`
NOTES

echo
echo "==> Ready: $out_dir/$apk_name"
echo "    SHA-256 $sha"
echo "    Release notes:"
sed 's/^/    | /' "$notes"
echo

if $dry_run; then
  echo "Dry run: nothing published."
  exit 0
fi

read -r -p "Publish GitHub release $tag from master? [y/N] " answer
[[ "$answer" == [yY] ]] || { echo "Cancelled."; exit 0; }
gh release create "$tag" "$out_dir/$apk_name" "$out_dir/SHA256SUMS" \
  --target master --title "Taskora $version" --notes-file "$notes"
