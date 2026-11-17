#!/bin/sh
# 构建 文学时钟 APK
#
# 需要：
#   - JDK 11+（javac / jarsigner 在 PATH 里）
#   - Android SDK build-tools（aapt2、zipalign）与 android.jar
#   - r8.jar（Android SDK 里自带；或从 https://r8.googlesource.com/ 下载）
#
# 可用环境变量覆盖：
#   ANDROID_JAR   android.jar 路径（默认取 $ANDROID_HOME/platforms/android-34/android.jar）
#   BUILD_TOOLS   build-tools 目录（默认取 $ANDROID_HOME/build-tools/34.0.0）
#   R8            r8.jar 路径（默认 tools/r8.jar 或 $ANDROID_HOME/lib/r8.jar）
#   KEYSTORE      签名用 keystore（默认 release.jks）
set -e

APP=$(cd "$(dirname "$0")" && pwd)
OUT="$APP/build"

ANDROID_HOME=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}

# build-tools：默认 34.0.0，没有就用现有的最高版本
BUILD_TOOLS=${BUILD_TOOLS:-$ANDROID_HOME/build-tools/34.0.0}
[ -d "$BUILD_TOOLS" ] || BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)

# android.jar：默认 android-34，没有就用现有的最高版本
ANDROID_JAR=${ANDROID_JAR:-$ANDROID_HOME/platforms/android-34/android.jar}
[ -f "$ANDROID_JAR" ] || ANDROID_JAR=$(ls "$ANDROID_HOME"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)

# r8：仓库内 → build-tools/lib → SDK/lib
R8=${R8:-$APP/tools/r8.jar}
[ -f "$R8" ] || R8="$BUILD_TOOLS/lib/r8.jar"
[ -f "$R8" ] || R8="$ANDROID_HOME/lib/r8.jar"
KEYSTORE=${KEYSTORE:-$APP/release.jks}

echo "ANDROID_HOME=$ANDROID_HOME"
echo "BUILD_TOOLS=$BUILD_TOOLS"
echo "ANDROID_JAR=$ANDROID_JAR"
echo "R8=$R8"
for f in "$ANDROID_JAR" "$BUILD_TOOLS/aapt2" "$BUILD_TOOLS/zipalign" "$R8"; do
  [ -e "$f" ] || { echo "缺少依赖: $f" >&2; exit 1; }
done
[ -f "$KEYSTORE" ] || { echo "缺少签名文件: $KEYSTORE（见 README「签名」一节）" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

echo "[1/5] javac"
javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find "$APP/src" -name '*.java') 2>&1 | grep -v 'bootstrap class path' || true
find "$OUT/classes" -name '*.class' | head -1 >/dev/null || { echo "ERROR: 没有生成 class" >&2; exit 1; }

echo "[2/5] d8"
java -cp "$R8" com.android.tools.r8.D8 --release --min-api 26 --lib "$ANDROID_JAR" \
  --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "[3/5] aapt2 compile + link"
"$BUILD_TOOLS/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"
"$BUILD_TOOLS/aapt2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -A "$APP/assets" -0 ttf \
  --min-sdk-version 26 --target-sdk-version 29 --auto-add-overlay \
  "$OUT/res.zip"

echo "[4/5] 打 dex + 对齐"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
python3 - "$OUT/unaligned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print("added classes.dex")
PY
"$BUILD_TOOLS/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "[5/5] 签名"
cp "$OUT/aligned.apk" "$OUT/signed_in.apk"
rm -f "$OUT/signed.apk"
jarsigner -keystore "$KEYSTORE" -storepass "${STORE_PASS:-etasign}" -keypass "${KEY_PASS:-etasign}" \
  -digestalg SHA-256 -sigalg SHA256withRSA \
  -signedjar "$OUT/signed.apk" "$OUT/signed_in.apk" "${KEY_ALIAS:-clock}" >/dev/null
cp "$OUT/signed.apk" "$APP/clock.apk"

ls -lh "$APP/clock.apk"
echo "OK: $APP/clock.apk"
