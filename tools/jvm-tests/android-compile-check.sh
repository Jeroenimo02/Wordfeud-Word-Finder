#!/usr/bin/env bash
# Compile-checks ALL extension sources (including ui/ and other android.* users) with javac
# against android.jar (platform 34) on the classpath (java.* APIs are checked against JDK 17).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
CACHE="$HERE/.cache"
JAR="$CACHE/android.jar"
URL="https://dl.google.com/android/repository/platform-34-ext7_r02.zip"
SRC="$HERE/../../extensions/extension/src/main/java"
OUT="$HERE/build/android-compile-check"

if [ ! -f "$JAR" ]; then
  mkdir -p "$CACHE/dl"
  curl -fsSL "$URL" -o "$CACHE/platform.zip"
  unzip -q -o "$CACHE/platform.zip" -d "$CACHE/dl"
  cp "$CACHE"/dl/*/android.jar "$JAR"
  rm -rf "$CACHE/dl" "$CACHE/platform.zip"
fi

rm -rf "$OUT"; mkdir -p "$OUT"
mapfile -t FILES < <(find "$SRC" -name '*.java')
if [ "${#FILES[@]}" -eq 0 ]; then
  echo "android-compile-check: no Java sources yet, nothing to compile"
  exit 0
fi
javac --release 17 -Xlint:-options -classpath "$JAR" -d "$OUT" "${FILES[@]}"
echo "android-compile-check: OK (${#FILES[@]} files)"
