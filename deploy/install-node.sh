#!/usr/bin/env bash
set -euo pipefail
# Installs a pinned official Node binary and checks its published SHA-256.
[[ "$EUID" -eq 0 ]] || { echo "Run: sudo bash deploy/install-node.sh" >&2; exit 1; }
version=24.19.0
case "$(uname -m)" in x86_64) arch=x64 ;; aarch64|arm64) arch=arm64 ;; *) echo "Unsupported CPU architecture" >&2; exit 1 ;; esac
archive="node-v$version-linux-$arch.tar.xz"
prefix="/opt/node-v$version-linux-$arch"
if [[ ! -x "$prefix/bin/node" ]]; then
  scratch="$(mktemp -d)"
  trap 'rm -rf "$scratch"' EXIT
  curl -fSL --retry 3 "https://nodejs.org/dist/v$version/$archive" -o "$scratch/$archive"
  curl -fsSL --retry 3 "https://nodejs.org/dist/v$version/SHASUMS256.txt" -o "$scratch/SHASUMS256.txt"
  (cd "$scratch"; grep " $archive$" SHASUMS256.txt | sha256sum --check --status)
  tar -xJf "$scratch/$archive" -C /opt
fi
for program in node npm npx; do ln -sfn "$prefix/bin/$program" "/usr/local/bin/$program"; done
/usr/local/bin/node --version
