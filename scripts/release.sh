#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE_PATH="$(security find-generic-password -a "$USER" -s FilmVaultReleaseKeystore -w)"
SIGNING_PASSWORD="$(security find-generic-password -a "$USER" -s FilmVaultReleaseSigning -w)"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "Release keystore not found: $KEYSTORE_PATH" >&2
  exit 1
fi

export FILMVAULT_KEYSTORE_PATH="$KEYSTORE_PATH"
export FILMVAULT_STORE_PASSWORD="$SIGNING_PASSWORD"
export FILMVAULT_KEY_PASSWORD="$SIGNING_PASSWORD"
export FILMVAULT_KEY_ALIAS="filmvault"

cd "$PROJECT_DIR"
if [[ -z "${JAVA_HOME:-}" && -d "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]]; then
  export JAVA_HOME="/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
fi
GRADLE_BIN="${GRADLE_BIN:-}"
if [[ -z "$GRADLE_BIN" ]]; then
  GRADLE_BIN="$(command -v gradle || true)"
fi
if [[ -z "$GRADLE_BIN" ]]; then
  GRADLE_BIN="/Users/waterchestnut/.gradle/wrapper/dists/gradle-9.2.1-bin/2t0n5ozlw9xmuyvbp7dnzaxug/gradle-9.2.1/bin/gradle"
fi
"$GRADLE_BIN" :app:assembleRelease --no-daemon
echo "Release APK: $PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
