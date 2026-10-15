#!/usr/bin/env bash
set -e

echo "=== LAN Buddy Voice (com.lanbuddy.voice) APK Builder ==="
if command -v gradle >/dev/null 2>&1; then
  echo "[1/2] Running Gradle assembleDebug..."
  gradle assembleDebug --no-daemon
elif [ -f "./gradlew" ]; then
  chmod +x ./gradlew
  ./gradlew assembleDebug --no-daemon
else
  echo "Error: Gradle not found. Install gradle or push this folder to GitHub to run .github/workflows/build-apk.yml"
  exit 1
fi

echo "[2/2] Success! Installable APK generated at:"
echo "  -> app/build/outputs/apk/debug/app-debug.apk"
