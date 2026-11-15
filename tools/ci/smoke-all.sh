#!/usr/bin/env bash
# Runs the smoke test twice on the same emulator: the normal layout (address bar at the bottom, the full set of steps) and the
# address bar at the top (layout steps only). Fails if either failed.
PKG=com.killswitch.sheer
bash tools/ci/smoke-test.sh smoke-bottom.apk "$PKG" bottom full; a=$?
bash tools/ci/smoke-test.sh smoke-top.apk "$PKG" top layout; b=$?
echo "bottom=$a top=$b"
[ "$a" = 0 ] && [ "$b" = 0 ]
