#!/usr/bin/env bash
# Runs the smoke test twice on the same emulator: the normal layout (address bar at the bottom, the full set of steps) and the
# address bar at the top (layout steps only). Fails if either failed.
PKG=com.killswitch.sheer
bash tools/ci/smoke-test.sh smoke-bottom.apk "$PKG" bottom "${SMOKE_MODE:-quick}"; a=$?
b=0
if [ -f smoke-top.apk ]; then bash tools/ci/smoke-test.sh smoke-top.apk "$PKG" top layout; b=$?; fi
# What pages ask for (alerts, lists, location...): the screenshots are read by a person, they do not decide whether the build is good.
if [ -f smoke-bottom.apk ]; then bash tools/ci/feature-test.sh smoke-bottom.apk "$PKG" || true; fi
# The compile experiment (tools/ci/compile-test.sh) has answered its question (compiling changed neither start nor scroll on the emulator), so it no longer runs every build.
echo "bottom=$a top=$b"
[ "$a" = 0 ] && [ "$b" = 0 ]
