#!/usr/bin/env bash
# Builds the Google Play App Bundle (.aab). Same app as build.sh, except the
# donation link is removed (Play does not allow links to outside payments).
# Needs: aapt2, javac, dalvik-exchange or d8, bundletool.jar, jarsigner, signing.env
set -euo pipefail
cd "$(dirname "$0")"
source signing.env
: "${ANDROID_JAR:?set ANDROID_JAR}"; : "${BUNDLETOOL:?set BUNDLETOOL to bundletool-all.jar}"
A=build/play; rm -rf "$A"; mkdir -p "$A"/{gen,obj,mod/manifest,mod/dex,x,src} dist
cp -r src/. "$A/src/"
python3 - "$A/src/com/filmscan/app/AboutActivity.java" <<'PY'
import sys,re; p=sys.argv[1]; s=open(p).read()
s=re.sub(r'    /\*\* Donation page[^\n]*\n    static final String DONATE_URL = [^\n]*\n','',s)
s='\n'.join(l for l in s.split('\n') if 'DONATE_URL' not in l)
open(p,'w').write(s)
PY
aapt2 compile --dir res -o "$A/res.zip"
aapt2 link --proto-format -o "$A/base.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java "$A/gen" "$A/res.zip" --auto-add-overlay
find "$A/src" "$A/gen" -name '*.java' > "$A/srcs.txt"
javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d "$A/obj" @"$A/srcs.txt"
if command -v d8 >/dev/null; then d8 --min-api 24 --lib "$ANDROID_JAR" --output "$A/mod/dex" $(find "$A/obj" -name '*.class')
else dalvik-exchange --dex --min-sdk-version=24 --output="$A/mod/dex/classes.dex" "$A/obj"; fi
(cd "$A/x" && unzip -q ../base.apk && mv AndroidManifest.xml ../mod/manifest/ && mv resources.pb res ../mod/)
(cd "$A/mod" && zip -qr ../base.zip manifest dex res resources.pb)
VER=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)
OUT="dist/RadioFilm-Scanner-v$VER.aab"; rm -f "$OUT"
java -jar "$BUNDLETOOL" build-bundle --modules="$A/base.zip" --output="$OUT"
jarsigner -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASSWORD" -keypass "$KEY_PASSWORD" -sigalg SHA256withRSA -digestalg SHA-256 "$OUT" "$KEY_ALIAS" >/dev/null
java -jar "$BUNDLETOOL" validate --bundle="$OUT" >/dev/null
echo "Done: $OUT"
