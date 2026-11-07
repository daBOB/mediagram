#!/usr/bin/env bash
# Re-sign and reinstall the Spieltisch controller app on every household iPhone and iPad.
#
# A free Apple ID signs sideloaded apps for 7 days; after that the app does not open at all.
# Run this before game night (at least weekly). Each device must be plugged in by USB, or
# reachable over Wi-Fi once it has been paired by cable and trusted.
#
# It also leaves the unsigned .ipa at ios/controller/xtool/Spieltisch.ipa for Sideloadly.
set -uo pipefail

export PATH="$HOME/.local/share/swiftly/bin:$HOME/.local/bin:$PATH"
cd "$(dirname "$0")/../ios/controller" || exit 1

out=$(xtool dev build --ipa 2>&1) || { echo "$out"; echo "Bauen fehlgeschlagen."; exit 1; }
ipa=$(printf '%s\n' "$out" | sed -n 's/^Wrote to //p' | tail -n 1)
[ -f "$ipa" ] || { echo "$out"; echo "Keine .ipa gefunden."; exit 1; }
echo "App gebaut: $ipa"

devices=$({ idevice_id -l; idevice_id -n; } 2>/dev/null | sort -u)
[ -n "$devices" ] || { echo "Kein iPhone oder iPad gefunden. Per USB anschließen und entsperren."; exit 1; }

failed=0
for udid in $devices; do
  name=$(ideviceinfo -u "$udid" -n -k DeviceName 2>/dev/null || ideviceinfo -u "$udid" -k DeviceName 2>/dev/null || echo "$udid")
  if xtool install --udid "$udid" "$ipa"; then
    echo "$name: aktualisiert (läuft wieder 7 Tage)"
  else
    echo "$name: FEHLER"
    failed=1
  fi
done
exit "$failed"
