#!/usr/bin/env bash
# Pinned upstream binary for the supported WSL2/Linux x86_64 environment.
set -euo pipefail
test "$(uname -s)" = Linux && test "$(uname -m)" = x86_64
install_dir="${1:?Usage: install-jj.sh <binary-directory>}"
mkdir -p "$install_dir"
install_dir="$(cd "$install_dir" && pwd)"
staging="$(mktemp -d)"
trap 'rm -rf -- "$staging"' EXIT
curl -fL --retry 3 https://github.com/jj-vcs/jj/releases/download/v0.47.1/jj-v0.47.1-x86_64-unknown-linux-musl.tar.gz -o "$staging/jj.tar.gz"
printf '%s  %s\n' d4b657f622ee50929c8916466dfa3d6a6c17a0a32b50e7b2c793707ec9ebcad7 "$staging/jj.tar.gz" | sha256sum -c -
tar -xzf "$staging/jj.tar.gz" -C "$install_dir" ./jj
"$install_dir/jj" --version
