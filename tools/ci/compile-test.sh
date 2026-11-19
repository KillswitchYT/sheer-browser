#!/usr/bin/env bash
# Does the state of Android's compiled code change how Sheer starts and scrolls?
#   tools/ci/compile-test.sh <apk> <package>
# Every update throws the compiled code away, and the phone only redoes it while idle and charging. This installs the app four ways
# and, for each one, prints the compile state, three cold starts and the frame stats of a scroll:
#   A fresh install (what an update leaves)   B speed-profile (what the nightly job does)   C full compile (the best case)
#   D installed together with a .dm profile (what the in-app updater could do, so it is compiled at install)
# HONEST LIMITS: an emulator with software graphics is not a Samsung. It shows the CPU side (code speed), not the GPU side.
set -u
APK="${1:?need the apk path}"; PKG="${2:?need the package name}"
LOG="smoke-test-compile.log"; : > "$LOG"
exec > >(tee -a "$LOG") 2>&1
adbx() { adb shell "$@" 2>&1 | tr -d '\r'; }
adb root > /dev/null 2>&1; sleep 3

state() {
  echo "-- compile state"
  adbx dumpsys package dexopt | grep -A3 "\[$PKG\]" | tr -s ' ' | head -5
  adbx "ls -l /data/app/*/$PKG*/oat/*/ 2>/dev/null" | awk '{print $5, $NF}'
}
cold() {
  echo "-- three cold starts (ms until the first screen)"
  for i in 1 2 3; do
    adbx am force-stop "$PKG"; sleep 2
    adbx am start -W -n "$PKG/.MainActivity" | grep -E "TotalTime" | tr '\n' ' '; echo
    sleep 5
  done
}
scroll() {
  echo "-- 30 swipes down a long page, frame stats from the phone's own counter"
  adbx am force-stop "$PKG"; sleep 2
  adbx am start -W -a android.intent.action.VIEW -d "https://en.wikipedia.org/wiki/Firefox" -n "$PKG/.MainActivity" > /dev/null
  sleep 15
  adbx dumpsys gfxinfo "$PKG" reset > /dev/null
  for i in $(seq 1 30); do adbx input swipe 540 1750 540 450 60 > /dev/null; sleep 0.4; done
  adbx dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile"
}
run() { echo; echo "=================== $1"; state; cold; scroll; }

adb uninstall "$PKG" > /dev/null 2>&1 || true
adb install -r "$APK" 2>&1 | tail -1
run "A  fresh install, nothing compiled yet (what every update leaves)"

# The profile is written by the app a few seconds after it has started; after the runs above it exists.
adbx cmd package compile -m speed-profile -f "$PKG" | tail -1
run "B  compiled with the profile Sheer ships (what the nightly job does)"

adbx cmd package compile -m speed -f "$PKG" | tail -1
run "C  everything compiled (the best case)"

echo; echo "=================== D  installed together with a .dm file"
rm -rf dmtest && mkdir dmtest && cp "$APK" dmtest/Sheer.apk
unzip -p "$APK" assets/dexopt/baseline.prof > dmtest/primary.prof
unzip -p "$APK" assets/dexopt/baseline.profm > dmtest/primary.profm
ls -l dmtest | awk '{print $5, $NF}'
( cd dmtest && zip -q Sheer.dm primary.prof primary.profm )
adb uninstall "$PKG" > /dev/null 2>&1 || true
adb install-multiple -r dmtest/Sheer.apk dmtest/Sheer.dm 2>&1 | tail -2
sleep 3
state
cold
scroll
echo; echo "compile test finished"
