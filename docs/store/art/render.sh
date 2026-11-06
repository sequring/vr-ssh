#!/usr/bin/env bash
# Renders Meta Horizon Store art from cover.html with headless Chrome,
# then flattens to 24-bit PNG (no alpha), as the store requires.
# Usage: docs/store/art/render.sh [python-with-Pillow]
set -euo pipefail
cd "$(dirname "$0")"

CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
PYTHON="${1:-python3}"
SRC="file://$PWD/cover.html"

render() {  # name width height [extra query]
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --window-size="$2,$3" --virtual-time-budget=2000 \
    --screenshot="$PWD/$1.png" "$SRC?w=$2&h=$3${4:-}" >/dev/null 2>&1
}

render hero-cover        3000 900
render cover-landscape   2560 1440
render cover-square      1440 1440
render cover-portrait    1008 1440
render mini-landscape    1080 360
render icon              512  512 "&icon=1"

"$PYTHON" - <<'EOF'
from PIL import Image
import glob
for f in sorted(glob.glob("*.png")):
    im = Image.open(f).convert("RGB")      # 24-bit, no alpha channel
    im.save(f, optimize=True)
    print(f"{f:22} {im.size[0]}x{im.size[1]} {im.mode}")
EOF
