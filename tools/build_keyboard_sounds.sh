#!/usr/bin/env bash
# Converts the CC0 keyboard samples into the app's sound assets.
# Usage: tools/build_keyboard_sounds.sh /path/to/mechanify/Resources/Profiles
# Sources and licences: docs/ASSETS.md. Needs ffmpeg.
set -euo pipefail

SRC="${1:?path to the Mechanify Resources/Profiles directory}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/sounds"

# set name -> source profile
declare -A PROFILE=( [thocky]="Cherry MX Red" [tactile]="Cherry MX Clear" [clicky]="Cherry MX Blue" )

# Peak-normalise to -3 dB so every set sits at a similar level.
convert() { # in out [extra ffmpeg filter]
    local in="$1" out="$2" extra="${3:-}"
    local peak gain
    peak=$(ffmpeg -hide_banner -i "$in" -af "volumedetect${extra:+,}" -f null - 2>&1 | sed -n 's/.*max_volume: \(-\?[0-9.]*\) dB.*/\1/p' | head -1)
    gain=$(python3 -c "print(-3.0 - float('${peak:-0}'))")
    ffmpeg -y -hide_banner -loglevel error -i "$in" \
        -af "${extra:+$extra,}aresample=48000,pan=mono|c0=c0,volume=${gain}dB" \
        -c:a libvorbis -q:a 4 "$out"
}

for set in "${!PROFILE[@]}"; do
    dir="$OUT/$set"; rm -rf "$dir"; mkdir -p "$dir"
    prof="${PROFILE[$set]}"
    n=0
    for f in "$SRC/$prof/down/"*.wav; do
        case "$f" in *-p97.wav|*-p103.wav) continue;; esac
        convert "$f" "$dir/down_$(printf %02d $n).ogg"
        # Deeper stabiliser variant for Space, Enter, Backspace and Shift: pitch down about 20 %.
        convert "$f" "$dir/down_deep_$(printf %02d $n).ogg" "asetrate=48000*0.8,aresample=48000" || true
        n=$((n+1))
    done
    n=0
    for f in "$SRC/$prof/up/"*.wav; do
        case "$f" in *-p97.wav|*-p103.wav) continue;; esac
        convert "$f" "$dir/up_$(printf %02d $n).ogg"
        convert "$f" "$dir/up_deep_$(printf %02d $n).ogg" "asetrate=48000*0.8,aresample=48000" || true
        n=$((n+1))
    done
    echo "$set: $(ls "$dir" | wc -l) files"
done
