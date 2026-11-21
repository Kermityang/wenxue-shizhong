#!/bin/sh
# 构建脚本：本地与 CI 通用。
# 路径全部由脚本位置和 ANDROID_SDK_ROOT 推导，不写死 /opt/asdk、/workspace，
# 这样 GitHub Actions 上跑同一份 build.sh 也能过（CI 只做编译体检，产出的包不发布）。
set -e
APP=$(cd "$(dirname "$0")" && pwd)
OUT=$APP/build

# ---- 找 Android 构建工具 ----
# 优先级：显式环境变量 → 本机 /opt/asdk → ANDROID_SDK_ROOT 里最新的 build-tools
if [ -n "${CLOCK_BUILD_TOOLS:-}" ]; then
    BT=$CLOCK_BUILD_TOOLS
elif [ -d /opt/asdk/ast/build-tools ]; then
    BT=/opt/asdk/ast/build-tools
elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT/build-tools" ]; then
    BT=$(ls -d "$ANDROID_SDK_ROOT"/build-tools/* 2>/dev/null | sort -V | tail -1)
else
    echo "ERROR: 找不到 Android build-tools（可设 CLOCK_BUILD_TOOLS 或 ANDROID_SDK_ROOT）" >&2
    exit 1
fi

# ---- 找 android.jar ----
if [ -n "${CLOCK_ANDROID_JAR:-}" ]; then
    ANDROID_JAR=$CLOCK_ANDROID_JAR
elif [ -f /opt/asdk/pf/android-34/android.jar ]; then
    ANDROID_JAR=/opt/asdk/pf/android-34/android.jar
elif [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    ANDROID_JAR=$(ls "$ANDROID_SDK_ROOT"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)
fi
if [ -z "${ANDROID_JAR:-}" ] || [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: 找不到 android.jar（可设 CLOCK_ANDROID_JAR）" >&2
    exit 1
fi

# ---- 找 d8：优先 build-tools 自带的可执行文件，其次独立 r8.jar ----
R8JAR=${CLOCK_R8_JAR:-/workspace/tools/r8.jar}
if [ -x "$BT/d8" ]; then
    D8="$BT/d8"
else
    D8=""
fi

echo "  工具链: $BT"
echo "  android.jar: $ANDROID_JAR"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

echo "[0/5] 字体子集"
FONT_SRC="$APP/assets/LXGWWenKai-Light.ttf"
FONT_OUT="$FONT_SRC"
CORPUS=${CLOCK_CORPUS:-}
if [ -n "$CORPUS" ] && [ -f "$CORPUS" ]; then
  FONT_OUT="$OUT/clock-subset.ttf"          # 私域语料：子集另存，不覆盖公开版字体
fi
# 没装 fontTools 的环境（如 CI）跳过重切，直接用仓库里已有的子集
sh "$APP/tools/make_subset.sh" "" "$FONT_OUT" || true

echo "[1/5] javac"
javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find "$APP/src" -name '*.java') 2>&1 | grep -v 'bootstrap class path' || true

if [ -z "$(find "$OUT/classes" -name '*.class' 2>/dev/null | head -1)" ]; then
  echo "ERROR: no class files produced" >&2
  exit 1
fi

echo "[2/5] d8"
if [ -n "$D8" ]; then
  "$D8" --release --min-api 26 --lib "$ANDROID_JAR" \
    --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
else
  java -cp "$R8JAR" com.android.tools.r8.D8 --release --min-api 26 --lib "$ANDROID_JAR" \
    --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
fi

echo "[3/5] aapt2 compile + link"
# 语料打包：默认用仓库里的公版语料；CLOCK_CORPUS=/path/to/full.json 可切到完整版（本地自用）
rm -rf "$OUT/assets"
cp -r "$APP/assets" "$OUT/assets"
CORPUS=${CLOCK_CORPUS:-}
if [ -n "$CORPUS" ] && [ -f "$CORPUS" ]; then
  cp "$CORPUS" "$OUT/assets/clock.json"
  [ -f "$FONT_OUT" ] && [ "$FONT_OUT" != "$FONT_SRC" ] && cp "$FONT_OUT" "$OUT/assets/LXGWWenKai-Light.ttf"
  echo "  语料: $CORPUS（字体子集随语料重切）"
else
  echo "  语料: 公版 assets/clock.json"
fi
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"
# 版本号：aapt2 的 --version-code 不覆盖 manifest，改写一份副本
MANIFEST="$APP/AndroidManifest.xml"
if [ -n "${CLOCK_VERSION_CODE:-}" ] || [ -n "${CLOCK_VERSION_NAME:-}" ]; then
  MANIFEST="$OUT/AndroidManifest.xml"
  cp "$APP/AndroidManifest.xml" "$MANIFEST"
  [ -n "${CLOCK_VERSION_CODE:-}" ] && sed -i "s/android:versionCode=\"[0-9]*\"/android:versionCode=\"$CLOCK_VERSION_CODE\"/" "$MANIFEST"
  [ -n "${CLOCK_VERSION_NAME:-}" ] && sed -i "s/android:versionName=\"[^\"]*\"/android:versionName=\"$CLOCK_VERSION_NAME\"/" "$MANIFEST"
fi
"$BT/aapt2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest "$MANIFEST" \
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
# 签名材料不进仓库：keystore 和口令都不提交。
#   CLOCK_KEYSTORE   keystore 路径；不给就依次找 $APP/release.jks、$APP/../clock-signing/release.jks
#   CLOCK_STOREPASS  口令；不给就读 keystore 同目录的 signing.env（一行 CLOCK_STOREPASS=xxx）
KEYSTORE=${CLOCK_KEYSTORE:-}
if [ -z "$KEYSTORE" ]; then
  for c in "$APP/release.jks" "$APP/../clock-signing/release.jks"; do
    if [ -f "$c" ]; then KEYSTORE=$c; break; fi
  done
fi
if [ -z "$KEYSTORE" ] || [ ! -f "$KEYSTORE" ]; then
  echo "ERROR: 找不到签名 keystore（用 CLOCK_KEYSTORE 指定，或放到 $APP/../clock-signing/release.jks）" >&2
  exit 1
fi
if [ -z "${CLOCK_STOREPASS:-}" ] && [ -f "$(dirname "$KEYSTORE")/signing.env" ]; then
  . "$(dirname "$KEYSTORE")/signing.env"
fi
if [ -z "${CLOCK_STOREPASS:-}" ]; then
  echo "ERROR: 未提供签名口令（export CLOCK_STOREPASS=…，或写 $(dirname "$KEYSTORE")/signing.env）" >&2
  exit 1
fi
export CLOCK_STOREPASS   # 口令经环境变量交给 jarsigner，不出现在命令行参数里
SIGNTMP=${TMPDIR:-/tmp}
cp "$OUT/aligned.apk" "$SIGNTMP/clk_in.apk"
rm -f "$SIGNTMP/clk_out.apk"
jarsigner -keystore "$KEYSTORE" -storepass:env CLOCK_STOREPASS \
  -digestalg SHA-256 -sigalg SHA256withRSA \
  -signedjar "$SIGNTMP/clk_out.apk" "$SIGNTMP/clk_in.apk" clock >/dev/null
cp "$SIGNTMP/clk_out.apk" "$APP/clock.apk"

ls -lh "$APP/clock.apk"
echo "OK: $APP/clock.apk"
