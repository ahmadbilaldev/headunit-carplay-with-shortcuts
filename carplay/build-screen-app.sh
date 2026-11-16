#!/bin/sh
# Builds the CarPlay screen app from the upstream source (github.com/riazosama/headunit-carplay, MIT)
# with plain SDK tools, so nothing prebuilt is installed. usage: build-screen-app.sh <out-dir>
set -e
OUT=${1:?out dir}; mkdir -p "$OUT"; OUT=$(cd "$OUT" && pwd)
SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}; BT=$SDK/build-tools/34.0.0; AJ=$SDK/platforms/android-34/android.jar
[ -d "$OUT/src" ] || git clone -q --depth 1 https://github.com/riazosama/headunit-carplay "$OUT/src"
R=$OUT/src/carplay-screen; rm -rf "$OUT/stubs" "$OUT/classes" "$OUT/dex"; mkdir -p "$OUT/stubs" "$OUT/classes" "$OUT/dex"
# Read $R/src before installing: it should be one activity, one watcher service and compile-only stubs.
javac -nowarn -source 8 -target 8 -bootclasspath "$AJ" -d "$OUT/stubs" $(find "$R/stubs" -name '*.java')
javac -nowarn -source 8 -target 8 -bootclasspath "$AJ" -cp "$OUT/stubs" -d "$OUT/classes" $(find "$R/src" -name '*.java')
"$BT/d8" --release --min-api 27 --lib "$AJ" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$AJ" --manifest "$R/AndroidManifest.xml"
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "${KS:-$HOME/.android/debug.keystore}" --ks-pass "pass:${KS_PASS:-android}" --out "$OUT/CarPlayScreen.apk" "$OUT/aligned.apk"
ls -la "$OUT/CarPlayScreen.apk"
