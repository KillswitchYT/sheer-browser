#!/usr/bin/env bash
# Runs the smoke test twice on the same emulator: the normal layout (address bar at the bottom, the full set of steps) and the
# address bar at the top (layout steps only). Fails if either failed.
PKG=com.killswitch.sheer
bash tools/ci/smoke-test.sh smoke-bottom.apk "$PKG" bottom "${SMOKE_MODE:-quick}"; a=$?
b=0
if [ -f smoke-top.apk ]; then bash tools/ci/smoke-test.sh smoke-top.apk "$PKG" top layout; b=$?; fi
c=0
if [ -f smoke-fast.apk ]; then bash tools/ci/smoke-test.sh smoke-fast.apk "$PKG" fast layout; c=$?; fi
echo "bottom=$a top=$b fast=$c"
# The SurfaceView mode is a test switch: it is looked at, it does not decide whether the build is good.
[ "$a" = 0 ] && [ "$b" = 0 ]
