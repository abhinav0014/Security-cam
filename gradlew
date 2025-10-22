#!/bin/sh
# Gradle wrapper script for Unix-like systems.

set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_USER_HOME="$DIR/.gradle"

if [ -z "$GRADLE_VERSION" ]; then
  GRADLE_VERSION=7.5.1
fi

if [ -z "$GRADLE_HOME" ]; then
  GRADLE_HOME="$DIR/gradle/wrapper/gradle-$GRADLE_VERSION"
fi

if [ ! -d "$GRADLE_HOME" ]; then
  echo "Gradle distribution not found. Downloading..."
  mkdir -p "$GRADLE_HOME"
  curl -L "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$DIR/gradle.zip"
  unzip -q "$DIR/gradle.zip" -d "$DIR/gradle/wrapper"
  rm "$DIR/gradle.zip"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"