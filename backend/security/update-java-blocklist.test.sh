#!/bin/sh
set -eu
base=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT HUP INT TERM
mkdir -p "$scratch/jdk/lib/security"
target="$scratch/jdk/lib/security/blocked.certs"
printf 'Algorithm=SHA-256\r\n# local policy\r\nAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\r\n' > "$target"
sh "$base/update-java-blocklist.sh" "$scratch/jdk"
grep -q '# local policy' "$target"
grep -q '^AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA' "$target"
cp "$target" "$scratch/once"
sh "$base/update-java-blocklist.sh" "$scratch/jdk"
cmp "$target" "$scratch/once"
while IFS= read -r fingerprint; do
  case "$fingerprint" in Algorithm=*|'#'*|'') continue ;; esac
  grep -qx "$fingerprint" "$target"
done < "$base/emudhra-blocked.certs"
printf 'Algorithm=SHA-1\n' > "$target"
cp "$target" "$scratch/unsupported"
if sh "$base/update-java-blocklist.sh" "$scratch/jdk"; then
  echo 'Expected unsupported algorithm to fail' >&2
  exit 1
fi
cmp "$target" "$scratch/unsupported"
echo 'Java blocklist preservation, idempotency and fail-closed tests passed'
