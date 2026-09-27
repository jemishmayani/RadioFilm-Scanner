#!/usr/bin/env bash
# Runs the desktop tests for the image-processing core and other plain-Java logic (JDK only, no Android).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/tests; rm -rf "$OUT"; mkdir -p "$OUT"
javac -nowarn -encoding UTF-8 -d "$OUT" src/com/filmscan/core/*.java src/com/filmscan/app/SavedQuery.java \
  test/*.java test/com/filmscan/core/*.java
for t in DetectTest SnapTest AspectTest PreviewGeometryTest RedactTest StripRenderTest LayoutGeometryTest SavedQueryTest; do
  echo "== $t"; java -cp "$OUT" "$t" | tail -3
done
for t in com.filmscan.core.MoireTest com.filmscan.core.FilterTest; do
  echo "== ${t##*.}"; java -cp "$OUT" "$t" | tail -5
done
