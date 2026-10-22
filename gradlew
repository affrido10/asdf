#!/usr/bin/env sh
# Lightweight Gradle bootstrap for this project. Downloads the pinned Gradle distribution on first use.
set -eu
GRADLE_VERSION="8.9"
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
CACHE_DIR="$GRADLE_USER_HOME/wrapper/dists/paperscan/gradle-$GRADLE_VERSION"
DIST_DIR="$CACHE_DIR/gradle-$GRADLE_VERSION"
ZIP_FILE="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  if [ ! -f "$ZIP_FILE" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 2 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_FILE"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP_FILE" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    else
      echo "Install curl or wget to download Gradle $GRADLE_VERSION." >&2
      exit 1
    fi
  fi
  if ! command -v unzip >/dev/null 2>&1; then
    echo "Install unzip to extract Gradle $GRADLE_VERSION." >&2
    exit 1
  fi
  unzip -q -o "$ZIP_FILE" -d "$CACHE_DIR"
fi
exec "$DIST_DIR/bin/gradle" -p "$BASE_DIR" "$@"
