#!/bin/bash
set -euo pipefail

ROOT="${1:-.}"

echo "Fixing executable bits under ${ROOT}"
find "$ROOT" -type f \( \
  -name '*.sh' -o \
  -name '*.pl' -o \
  -name 'pkg-config' -o \
  -name 'configure' -o \
  -name 'autogen.sh' -o \
  -name 'InstallCertificates' \
\) -exec chmod +x {} +
