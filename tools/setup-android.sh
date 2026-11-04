#!/usr/bin/env bash
# Local Android toolchain: no sudo, everything under ~/android-dev.
set -euo pipefail
ROOT="$HOME/android-dev"
DL="$ROOT/downloads"
mkdir -p "$DL" "$ROOT/sdk"

fetch() { # url outfile
  [ -f "$2" ] || curl -fL --retry 3 -o "$2" "$1"
}

echo "== JDK 21 =="
fetch "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" "$DL/jdk21.tar.gz"
if [ ! -d "$ROOT/jdk-21" ]; then
  mkdir -p "$ROOT/jdk-21"
  tar xzf "$DL/jdk21.tar.gz" -C "$ROOT/jdk-21" --strip-components=1
fi
export JAVA_HOME="$ROOT/jdk-21"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1

echo "== cmdline-tools =="
fetch "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" "$DL/cmdline-tools.zip"
if [ ! -d "$ROOT/sdk/cmdline-tools/latest" ]; then
  mkdir -p "$ROOT/sdk/cmdline-tools"
  unzip -q -o "$DL/cmdline-tools.zip" -d "$ROOT/sdk/cmdline-tools"
  mv "$ROOT/sdk/cmdline-tools/cmdline-tools" "$ROOT/sdk/cmdline-tools/latest"
fi

echo "== gradle 8.13 =="
fetch "https://services.gradle.org/distributions/gradle-8.13-bin.zip" "$DL/gradle.zip"
if [ ! -d "$ROOT/gradle-8.13" ]; then
  unzip -q -o "$DL/gradle.zip" -d "$ROOT"
fi

export ANDROID_HOME="$ROOT/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ROOT/gradle-8.13/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

echo "== sdk packages =="
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-35" "platforms;android-36" "build-tools;35.0.0" 2>&1 | tail -3

echo "== done =="
"$ROOT/gradle-8.13/bin/gradle" --version 2>&1 | grep -E "^Gradle|^JVM" | head -2
ls "$ANDROID_HOME/platforms" "$ANDROID_HOME/build-tools"
