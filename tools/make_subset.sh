#!/bin/sh
# 从霞鹜文楷全字体重新切子集：只保留 App 实际用到的字，避免缺字掉回系统字体。
# 用法：sh tools/make_subset.sh [全字体路径]
set -e
APP=$(cd "$(dirname "$0")/.." && pwd)
FULL=${1:-/workspace/fonts/LXGWWenKai-Light.ttf}

if [ ! -f "$FULL" ]; then
  echo "  skip: 找不到全字体 $FULL（保留现有子集）"
  exit 0
fi

PYFT=$(command -v pyftsubset 2>/dev/null || echo /workspace/.venv-font/bin/pyftsubset)
if [ ! -x "$PYFT" ]; then
  echo "  skip: 没有 pyftsubset（pip install fonttools）"
  exit 0
fi

TMP=$(mktemp)
python3 "$APP/tools/collect_chars.py" > "$TMP"
echo "  字集 $(wc -m < "$TMP") 个字"

"$PYFT" "$FULL" \
  --text-file="$TMP" \
  --output-file="$APP/assets/LXGWWenKai-Light.ttf" \
  --layout-features='*' --glyph-names --symbol-cmap --legacy-cmap \
  --notdef-glyph --notdef-outline --recommended-glyphs \
  --name-IDs='*' --name-legacy --name-languages='*' --drop-tables+=DSIG
rm -f "$TMP"
echo "  子集 $(du -h "$APP/assets/LXGWWenKai-Light.ttf" | cut -f1)"
