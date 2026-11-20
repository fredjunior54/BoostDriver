#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
file="$root/version.properties"
readme="$root/README.md"
marker='<!-- app-version -->'
end_marker='<!-- /app-version -->'
die() {
    printf 'Version sync failed: %s\n' "$1" >&2
    exit 1
}

[[ $# -eq 0 || ( $# -eq 1 && ${1-} == --check ) ]] || die 'usage: scripts/bump-version.sh [--check]'
mapfile -t version < "$file"
[[ ${#version[@]} -eq 2 ]] || die 'expected exactly two lines in version.properties'
[[ ${version[0]} =~ ^versionName=(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]{0,8})$ ]] ||
    die 'expected versionName=MAJOR.MINOR.PATCH with a patch of at most nine digits'
patch=${BASH_REMATCH[3]}
prefix="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"
name=${version[0]#versionName=}
[[ ${version[1]} =~ ^versionCode=([1-9][0-9]{0,9})$ ]] || die 'expected a positive integer versionCode'
code=${BASH_REMATCH[1]}
(( code <= 2100000000 )) || die 'versionCode exceeds the Google Play limit'
[[ $(grep -oF -- "$marker" "$readme" | wc -l || true) -eq 1 &&
   $(grep -oF -- "$end_marker" "$readme" | wc -l || true) -eq 1 ]] || die 'expected exactly one README marker pair'
grep -Fxq -- "$marker **Version:** v$name $end_marker" "$readme" || die 'README version does not match version.properties'
if [[ ${1-} == --check ]]; then
    printf 'Version metadata is in sync: %s (versionCode %s).\n' "$name" "$code"
    exit 0
fi

(( code < 2100000000 )) || die 'increment would exceed the Google Play versionCode limit'
(( patch < 999999999 )) || die 'patch has reached its nine-digit limit'
next_name="$prefix.$(( patch + 1 ))"
next_code=$(( code + 1 ))

# Prepare both files before publishing either one. Each rename is atomic.
version_tmp=$(mktemp -- "$root/.version.properties.XXXXXX")
readme_tmp=
trap 'rm -f -- "$version_tmp" ${readme_tmp:+"$readme_tmp"}' EXIT
readme_tmp=$(mktemp -- "$root/.README.md.XXXXXX")
printf 'versionName=%s\nversionCode=%s\n' "$next_name" "$next_code" > "$version_tmp"
awk -v old="$marker **Version:** v$name $end_marker" -v new="$marker **Version:** v$next_name $end_marker" \
    '$0 == old {$0 = new} {print}' "$readme" > "$readme_tmp"
chmod --reference="$file" "$version_tmp"
chmod --reference="$readme" "$readme_tmp"
mv -f -- "$version_tmp" "$file"
mv -f -- "$readme_tmp" "$readme"
trap - EXIT
printf 'Bumped version to %s (versionCode %s).\n' "$next_name" "$next_code"
