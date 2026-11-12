#!/usr/bin/env bash
# Fails if the app source picks up an API or setting that would leak user data or weaken
# transport security. Run by the pre-commit hook and by CI. Comment lines are ignored.
set -euo pipefail
cd "$(dirname "$0")/.."

main=app/src/main
failed=0

# forbid <description> <extended regex> <path>...
forbid() {
  local description=$1 pattern=$2
  shift 2
  local hits
  hits=$(grep -rnE --include='*.kt' --include='*.xml' "$pattern" "$@" 2>/dev/null \
    | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*|<!--)' || true)
  if [[ -n $hits ]]; then
    echo "privacy-guard: $description" >&2
    echo "$hits" | sed 's/^/  /' >&2
    failed=1
  fi
}

forbid "DEVICE_NAME / bluetooth_name are the user-assigned device name; they must not be shared (use DefaultPlayerName)" \
  'DEVICE_NAME|"bluetooth_name"|"device_name"' "$main"
forbid "Hardware and advertising identifiers are tracking identifiers" \
  'ANDROID_ID|AdvertisingIdClient|getImei|getDeviceId|getSerial\(|Build\.SERIAL' "$main"
forbid "Logging can leak player names and photos into logcat" \
  '(android\.util\.Log\b|\bLog\.[vdiwe]\(|println\(|printStackTrace\()' "$main/java"
forbid "Cleartext traffic must stay disabled" \
  'usesCleartextTraffic="true"|cleartextTrafficPermitted="true"' "$main"
forbid "Plain-HTTP URLs send data in cleartext" \
  '"http://' "$main/java"
forbid "World-readable/writable files expose app data to other apps" \
  'MODE_WORLD_READABLE|MODE_WORLD_WRITEABLE' "$main"
forbid "Photos and app data must stay in app-private storage" \
  'getExternalStorage|getExternalFilesDir|getExternalCacheDir|MediaStore\.Images\.Media\.insert' "$main/java"

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "privacy-guard: OK"
