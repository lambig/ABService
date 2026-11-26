#!/bin/sh
# Merge the OpenJDK eMudhra denylist without replacing vendor/local entries.
set -eu
base=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
jdk=${1:-${JAVA_HOME:?Set JAVA_HOME or pass the JDK directory}}
target="$jdk/lib/security/blocked.certs"
if [ ! -f "$target" ]; then
  echo "Missing JDK blocklist: $target" >&2
  exit 1
fi
if ! tr -d '\r' < "$target" | grep -qx 'Algorithm=SHA-256'; then
  echo 'Unsupported JDK blocklist algorithm; no changes made.' >&2
  exit 1
fi
source="$base/emudhra-blocked.certs"
if ! awk '/^#/ || /^$/ || /^Algorithm=SHA-256$/ {next} length($0) != 64 || /[^0-9A-F]/ {exit 1}' "$source"; then
  echo 'Invalid denylist; no changes made.' >&2
  exit 1
fi
added=0
while IFS= read -r fingerprint; do
  case "$fingerprint" in ''|'#'*|Algorithm=*) continue ;; esac
  if ! tr -d '\r' < "$target" | grep -qx "$fingerprint"; then
    printf '\n%s\n' "$fingerprint" >> "$target"
    added=$((added + 1))
  fi
done < "$source"
echo "Java denylist: added $added fingerprints"
