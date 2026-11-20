#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
file="$root/version.properties"

die() {
    printf 'Version bump failed: %s\n' "$1" >&2
    exit 1
}

[[ $# -eq 0 ]] || die "usage: scripts/bump-version.sh"
mapfile -t version < "$file"
[[ ${#version[@]} -eq 2 ]] || die 'expected exactly two lines in version.properties'
[[ ${version[0]} =~ ^versionName=(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]{0,8})$ ]] ||
    die 'expected versionName=MAJOR.MINOR.PATCH with a patch of at most nine digits'
next_name="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$(( BASH_REMATCH[3] + 1 ))"
[[ ${version[1]} =~ ^versionCode=([1-9][0-9]{0,9})$ ]] || die 'expected a positive integer versionCode'
code=${BASH_REMATCH[1]}
(( code < 2100000000 )) || die 'increment would exceed the Google Play versionCode limit'
[[ ${next_name##*.} != 1000000000 ]] || die 'patch has reached its nine-digit limit'
next_code=$(( code + 1 ))

# The temporary file is on the same filesystem so readers see both values change together.
tmp=$(mktemp -- "$root/.version.properties.XXXXXX")
trap 'rm -f -- "$tmp"' EXIT
printf 'versionName=%s\nversionCode=%s\n' "$next_name" "$next_code" > "$tmp"
chmod 644 "$tmp"
mv -f -- "$tmp" "$file"
trap - EXIT
printf 'Bumped version to %s (versionCode %s).\n' "$next_name" "$next_code"
