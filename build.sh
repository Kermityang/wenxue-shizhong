#!/bin/sh
set -e
BT=/opt/asdk/ast/build-tools
ANDROID_JAR=/opt/asdk/pf/android-34/android.jar
R8=/workspace/tools/r8.jar
APP=/workspace/apps/clock
OUT=$APP/build

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

echo "[0/5] 字体子集"
sh "$APP/tools/make_subset.sh" || true

echo "[1/5] javac"
javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find "$APP/src" -name '*.java') 2>&1 | grep -v 'bootstrap class path' || true

if [ -z "$(find "$OUT/classes" -name '*.class' 2>/dev/null | head -1)" ]; then
  echo "ERROR: no class files produced" >&2
  exit 1
fi

echo "[2/5] d8"
java -cp "$R8" com.android.tools.r8.D8 --release --min-api 26 --lib "$ANDROID_JAR" \
  --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "[3/5] aapt2 compile + link"
# 语料打包：默认用仓库里的公版语料；CLOCK_CORPUS=/path/to/full.json 可切到完整版（本地自用）
rm -rf "$OUT/assets"
cp -r "$APP/assets" "$OUT/assets"
CORPUS=${CLOCK_CORPUS:-}
if [ -n "$CORPUS" ] && [ -f "$CORPUS" ]; then
  cp "$CORPUS" "$OUT/assets/clock.json"
  echo "  语料: 完整版 $CORPUS"
else
  echo "  语料: 公版 assets/clock.json"
fi
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -A "$OUT/assets" -0 ttf \
  --min-sdk-version 26 --target-sdk-version 29 --auto-add-overlay \
  "$OUT/res.zip"

echo "[4/5] pack dex + align"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
python3 - "$OUT/unaligned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print("added classes.dex")
PY
"$BT/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "[5/5] sign"
cp "$OUT/aligned.apk" /tmp/clk_in.apk
rm -f /tmp/clk_out.apk
jarsigner -keystore "$APP/release.jks" -storepass etasign -keypass etasign \
  -digestalg SHA-256 -sigalg SHA256withRSA \
  -signedjar /tmp/clk_out.apk /tmp/clk_in.apk clock >/dev/null
cp /tmp/clk_out.apk "$APP/clock.apk"

ls -lh "$APP/clock.apk"
echo "OK: $APP/clock.apk"
