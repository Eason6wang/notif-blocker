#!/usr/bin/env bash
# Build the signed release APK and publish it as the latest GitHub release (the in-app updater reads it).
# Usage: scripts/release.sh   (needs `gh` logged in as the repo owner and ~/.config/notif-blocker/keystore.properties)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="Eason6wang/notif-blocker"
[[ -f "$HOME/.config/notif-blocker/keystore.properties" ]] || { echo 'Missing release keystore: see README.' >&2; exit 1; }
if [[ -z "${JAVA_HOME:-}" ]]; then
  for j in /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/java-17-openjdk-amd64; do [[ -x "$j/bin/java" ]] && { JAVA_HOME="$j"; break; }; done
fi
export JAVA_HOME ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
# Minutes since 2025-10-10: every build is higher than the one before, so the phone sees it as an update.
CODE="$(( ($(date +%s) - 1760000000) / 60 ))"
NAME="1.0.$CODE"
cd "$ROOT"
./gradlew --no-daemon --max-workers=2 -Pnb.versionCode="$CODE" -Pnb.versionName="$NAME" testDebugUnitTest assembleRelease
OUT="$ROOT/dist"; mkdir -p "$OUT"
cp app/build/outputs/apk/release/app-release.apk "$OUT/notif-blocker.apk"
SHA="$(sha256sum "$OUT/notif-blocker.apk" | cut -d' ' -f1)"
SIZE="$(wc -c < "$OUT/notif-blocker.apk" | tr -d ' ')"
printf '{"versionCode":%s,"versionName":"%s","sha256":"%s","size":%s}\n' "$CODE" "$NAME" "$SHA" "$SIZE" > "$OUT/update.json"
git fetch -q origin main
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo 'Push main first; releasing only pushed commits.' >&2; exit 1; }
gh release create "v$NAME" "$OUT/notif-blocker.apk" "$OUT/update.json" --repo "$REPO" \
  --title "Notification Blocker $NAME" --notes "Build $NAME (commit $(git rev-parse --short HEAD))" --latest
echo "Published v$NAME"
