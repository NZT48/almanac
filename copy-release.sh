#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <version>" >&2
    echo "Example: $0 1.1.1" >&2
    exit 1
fi

version="$1"
src="app/build/outputs/apk/release/app-release.apk"
dest_dir="$HOME/Downloads/almanac"
dest="$dest_dir/almanac_${version}.apk"

if [[ ! -f "$src" ]]; then
    echo "Release APK not found at $src. Run ./gradlew assembleRelease first." >&2
    exit 1
fi

mkdir -p "$dest_dir"
cp "$src" "$dest"
echo "Copied to $dest"
