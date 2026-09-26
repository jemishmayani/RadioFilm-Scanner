#!/usr/bin/env bash
# Runs the desktop tests for the image-processing core and the viewfinder maths (plain JDK, no Android).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/tests; rm -rf "$OUT"; mkdir -p "$OUT"
javac -nowarn -encoding UTF-8 -d "$OUT" src/com/filmscan/core/*.java test/*.java test/com/filmscan/core/*.java
for t in DetectTest SnapTest AspectTest PreviewGeometryTest RedactTest StripRenderTest; do
  echo "== $t"; java -cp "$OUT" "$t" | tail -3
done
echo "== FilterTest"; java -cp "$OUT" com.filmscan.core.FilterTest | tail -5
