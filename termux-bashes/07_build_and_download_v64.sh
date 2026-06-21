#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "This helper now starts the v6.7 Device Safety build."
exec "$(dirname "$0")/07_build_and_download_v67.sh"
