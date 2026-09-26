#!/usr/bin/env bash
# Builds a signed, universal RadioFilm Scanner APK without Gradle:
# aapt -> javac -> d8 -> aapt -> zipalign -> apksigner.
# Output: dist/RadioFilm-Scanner-v<versionName>.apk
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
# newest installed build-tools and platform, unless given explicitly
BUILD_TOOLS="${BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)}"
ANDROID_JAR="${ANDROID_JAR:-$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1 || true)}"

tool() {
  if [ -n "$BUILD_TOOLS" ] && [ -x "$BUILD_TOOLS/$1" ]; then echo "$BUILD_TOOLS/$1"; else command -v "$1" || true; fi
}
AAPT="$(tool aapt)"; D8="$(tool d8)"; ZIPALIGN="$(tool zipalign)"; APKSIGNER="$(tool apksigner)"
DX="$(command -v dalvik-exchange || command -v dx || true)"

fail() { echo "error: $*" >&2; exit 1; }
[ -f "$ANDROID_JAR" ] || fail "android.jar not found. Install an SDK platform or set ANDROID_JAR."
[ -n "$AAPT" ] || fail "aapt not found. Install Android build-tools or set BUILD_TOOLS."
[ -n "$ZIPALIGN" ] && [ -n "$APKSIGNER" ] || fail "zipalign/apksigner not found (Android build-tools)."
[ -n "$D8" ] || [ -n "$DX" ] || fail "d8 not found (Android build-tools 28+)."
command -v javac >/dev/null || fail "javac not found. Install a JDK (17 or 21)."

VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
CODE="$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
echo "Building RadioFilm Scanner $VERSION (versionCode $CODE)"
echo "  platform: $ANDROID_JAR"

OUT=build
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/dex" dist

"$AAPT" package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/obj" @"$OUT/sources.txt"

if [ -n "$D8" ]; then
  (cd "$OUT/obj" && jar cf ../classes.jar .)
  "$D8" --release --min-api 21 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/classes.jar"
else
  "$DX" --dex --min-sdk-version=21 --output="$OUT/dex/classes.dex" "$OUT/obj"
fi

"$AAPT" package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F "$OUT/unaligned.apk" -0 arsc
(cd "$OUT/dex" && "$AAPT" add ../unaligned.apk classes.dex > /dev/null)
"$ZIPALIGN" -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

# signing settings come from signing.env (not in git) or the environment
[ -f signing.env ] && . ./signing.env
[ -n "${KEYSTORE:-}" ] || fail "no signing key. Copy signing.env.example to signing.env and fill it in."
[ -f "$KEYSTORE" ] || fail "key file not found: $KEYSTORE"
APK="dist/RadioFilm-Scanner-v$VERSION.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:${KEY_PASSWORD:-$KEYSTORE_PASSWORD}" \
  --out "$APK" "$OUT/aligned.apk"
"$APKSIGNER" verify "$APK"
echo "Done: $APK ($(du -h "$APK" | cut -f1))"
