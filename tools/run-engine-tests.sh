#!/usr/bin/env bash
# Compiles the pure logic (palettes and the address bar) and runs tools/EngineTest.kt on a plain JVM. Needs kotlinc 2.x and a JDK.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd); SRC=$ROOT/app/src/main/java/com/killswitch/sheer; T=${TMPDIR:-/tmp}/sheer-engine
rm -rf "$T"; mkdir -p "$T/out"
kotlinc -nowarn -d "$T/out" "$SRC/Palettes.kt" "$SRC/UrlInput.kt" "$ROOT/tools/EngineTest.kt"
KLIB=$(dirname "$(command -v kotlinc)")/../lib/kotlin-stdlib.jar
java -cp "$T/out:$KLIB" EngineTestKt
