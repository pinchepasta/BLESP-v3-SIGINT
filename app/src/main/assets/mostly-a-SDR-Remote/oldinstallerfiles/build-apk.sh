#!/usr/bin/env bash
# Build the debug APK. Needs JDK 17 + Android SDK.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo "[!] gradle-wrapper.jar missing - fetching"
  curl -fsSL -o gradle/wrapper/gradle-wrapper.jar \
    "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" \
    || { echo "fetch failed; run: gradle wrapper --gradle-version 8.7"; exit 1; }
fi

if [ ! -f local.properties ]; then
  [ -n "${ANDROID_SDK_ROOT:-}" ] && printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
  [ -n "${ANDROID_HOME:-}" ] && printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
fi

./gradlew assembleDebug
mkdir -p dist
cp -f app/build/outputs/apk/debug/app-debug.apk dist/sdr-neon-remote.apk
echo "APK -> $(pwd)/dist/sdr-neon-remote.apk"
