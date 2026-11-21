#!/usr/bin/env bash
# Fails if the merged release manifest asks for anything outside the approved list.
# Run after: ./gradlew :app:processReleaseMainManifest
set -euo pipefail
M="$(dirname "$0")/../app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml"
[ -f "$M" ] || { echo "merged manifest not found: $M" >&2; exit 2; }

ALLOWED='android.permission.BLUETOOTH_CONNECT
android.permission.BLUETOOTH
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
android.permission.POST_NOTIFICATIONS
android.permission.VIBRATE'

found=$(grep -oE 'uses-permission[^>]*android:name="[^"]+"' "$M" | sed -E 's/.*android:name="([^"]+)".*/\1/' | sort -u)
bad=0
while IFS= read -r p; do
    [ -z "$p" ] && continue
    # The app's own dynamic receiver permission is added by AndroidX and is harmless.
    case "$p" in *.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) continue;; esac
    if ! grep -qxF "$p" <<<"$ALLOWED"; then echo "unexpected permission: $p" >&2; bad=1; fi
done <<<"$found"
grep -q 'android.permission.INTERNET' "$M" && { echo "INTERNET permission present" >&2; bad=1; }
grep -q 'android:allowBackup="true"' "$M" && { echo "allowBackup is true" >&2; bad=1; }
[ "$bad" = 0 ] && echo "manifest ok: $(echo "$found" | wc -l) permissions"
exit $bad
