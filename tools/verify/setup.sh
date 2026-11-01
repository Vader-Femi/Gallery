#!/usr/bin/env bash
# Downloads what the checks need into a cache folder (about 110 MB, once): the Kotlin compiler and an android.jar.
# Needs: bash, curl, unzip, a JDK (17 or newer). Nothing here touches the Android SDK or Gradle.
set -euo pipefail
CACHE="${VERIFY_CACHE:-$HOME/.cache/gallery-verify}"
KOTLIN_VERSION="${KOTLIN_VERSION:-2.3.0}"
mkdir -p "$CACHE"

if [ ! -x "$CACHE/kotlinc/bin/kotlinc" ]; then
    echo "Downloading the Kotlin compiler $KOTLIN_VERSION ..."
    curl -fsSL -o "$CACHE/kotlin.zip" "https://github.com/JetBrains/kotlin/releases/download/v$KOTLIN_VERSION/kotlin-compiler-$KOTLIN_VERSION.zip"
    unzip -q -o "$CACHE/kotlin.zip" -d "$CACHE"
    rm "$CACHE/kotlin.zip"
fi

if [ ! -f "$CACHE/android.jar" ]; then
    echo "Downloading android.jar (API 33) ..."
    curl -fsSL -o "$CACHE/android.jar" "https://github.com/Sable/android-platforms/raw/master/android-33/android.jar"
fi

echo "Ready: $CACHE"
