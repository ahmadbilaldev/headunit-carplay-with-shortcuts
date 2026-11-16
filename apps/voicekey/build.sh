#!/bin/sh
# Builds and signs the voice-key app. usage: build.sh <out-dir>
set -e
D=$(cd "$(dirname "$0")" && pwd); OUT=$1
SDK=/opt/homebrew/share/android-commandlinetools; BT=$SDK/build-tools/34.0.0; AJ=$SDK/platforms/android-34/android.jar
export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export PATH=$JAVA_HOME/bin:$PATH
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex"
"$BT/aapt2" compile --dir "$D/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$AJ" --manifest "$D/AndroidManifest.xml" "$OUT/res.zip"
javac -source 1.8 -target 1.8 -bootclasspath "$AJ" -nowarn -d "$OUT/classes" "$D"/src/dev/bilal/voicekey/*.java
"$BT/d8" --release --lib "$AJ" --min-api 26 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "${KS:-$HOME/.android/debug.keystore}" --ks-pass "pass:${KS_PASS:-android}" --out "$OUT/VoiceKey.apk" "$OUT/aligned.apk"
ls -la "$OUT/VoiceKey.apk"
