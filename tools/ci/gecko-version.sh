#!/usr/bin/env bash
# Prints the newest stable GeckoView (arm64) version listed on Mozilla maven, for example 160.0.20261104091234, or nothing if it cannot be read.
U=https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview-arm64-v8a/maven-metadata.xml
curl -fsS -m 30 --retry 2 "$U" 2>/dev/null \
  | grep -oE '<version>[0-9]+\.0\.[0-9]{14}</version>' | sed -E 's/<\/?version>//g' \
  | sort -t. -k1,1n -k3,3n | tail -1
