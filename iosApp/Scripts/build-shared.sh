#!/bin/bash
# Xcode build phase: builds the shared Kotlin code as the Nidoo framework for the SDK and
# configuration Xcode is building. Xcode does not load the shell profile, so find JDK 17 here.
set -euo pipefail

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in \
    "$(/usr/libexec/java_home -v 17 2>/dev/null || true)" \
    "/opt/homebrew/opt/openjdk@17" \
    "/usr/local/opt/openjdk@17" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "error: no JDK 17 found. Install one (brew install openjdk@17) or set JAVA_HOME." >&2
  exit 1
fi
export JAVA_HOME

cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
