#!/usr/bin/env bash
# The "fake phone" smoke test. CI runs it on an Android emulator against the OPTIMIZED apk (the x86_64 build of the same code).
#   tools/ci/smoke-test.sh <apk> <package> <tag> <full|layout>
# It does what a person does and checks the app is still alive after each step, saving the phone's crash log and a screenshot per step:
#   start, open a web link, open a very long page and scroll to its end (is the end readable above the bars?),
#   [full only] kill the page process the way Android does (does the page come back by itself?), restart the app (is the tab restored?).
# HONEST LIMITS: an emulator is not a Samsung, it uses software graphics, and this build is x86_64 while the real app is arm64.
# A crash here is real. A pass here does not prove the phone is fine.
set -u
APK="${1:?need the apk path}"; PKG="${2:?need the package name}"; TAG="${3:-run}"; MODE="${4:-full}"
RESULT="smoke-result-$TAG.txt"; LOG="smoke-test-$TAG.log"
: > "$RESULT"; : > "$LOG"
exec > >(tee -a "$LOG") 2>&1

log()  { echo "[$(date +%T)] $*"; }
note() { echo "$*" | tee -a "$RESULT"; }
adbx() { adb shell "$@" 2>&1 | tr -d '\r'; }
alive() { [ -n "$(adbx pidof "$PKG")" ]; }
shot() { adb exec-out screencap -p > "smoke-$TAG-$1.png" 2>/dev/null || true; }

crash_lines() {
  adb logcat -d -b crash 2>&1 | tr -d '\r' | tail -80
  adb logcat -d -b main -b system 2>&1 | tr -d '\r' \
    | grep -E "FATAL EXCEPTION|Fatal signal|JNI DETECTED ERROR|AndroidRuntime|SIGSEGV|SIGABRT|has died|ANR in $PKG|Force finishing activity $PKG" | tail -80
}

# Wait up to $1 seconds, return early (1) as soon as the main process is gone.
hold() { local i; for i in $(seq 1 "$1"); do alive || return 1; sleep 1; done; return 0; }

step() { # name, seconds to watch
  if hold "$2"; then note "[$TAG] $1: PASS the app was still running after $2 s"; return 0; fi
  note "[$TAG] $1: FAIL the app died within $2 s"
  log "---- crash log"; crash_lines | tee "smoke-crash-$TAG.txt"
  return 1
}

view() { adbx am start -W -a android.intent.action.VIEW -d "$1" -n "$PKG/.MainActivity" > /dev/null; }

log "[$TAG] emulator: $(adbx getprop ro.product.cpu.abi) android $(adbx getprop ro.build.version.release) api $(adbx getprop ro.build.version.sdk)"
adb uninstall "$PKG" > /dev/null 2>&1 || true
adb logcat -c
out=$(adb install -r "$APK" 2>&1); echo "$out"
if ! echo "$out" | grep -q "Success"; then note "[$TAG] INSTALL: FAIL $out"; exit 1; fi
note "[$TAG] INSTALL: PASS"

log "cold start"
adbx am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 3
step "START" 25 || { shot 1; exit 1; }
shot 1

log "open a web link, the way another app would"
view "https://example.com"
step "LINK" 25 || { shot 2; exit 1; }
shot 2

if [ "$MODE" != "quick" ]; then
  log "a very long page: top, then scroll to the end"
  view "https://en.wikipedia.org/wiki/Firefox"
  step "LONGPAGE" 30 || { shot 3; exit 1; }
  shot 3
  for i in $(seq 1 45); do adbx input swipe 540 1750 540 450 40 > /dev/null; sleep 0.3; done
  sleep 4
  step "SCROLL" 5 || { shot 4; exit 1; }
  shot 4
fi

if [ "$MODE" = "full" ]; then
  log "kill the page process, the way Android does when memory runs low"
  adb root > /dev/null 2>&1; sleep 3
  pids=$(adbx ps -A | grep -E "$PKG:" | awk '{print $2}')
  echo "child processes: $(adbx ps -A | grep -E "$PKG:" | awk '{print $2, $NF}' | tr '\n' ';')"
  killed=0
  for p in $(adbx ps -A | grep -E "$PKG:(sandboxed_)?tab" | awk '{print $2}'); do adbx kill -9 "$p" > /dev/null; killed=$((killed + 1)); done
  note "[$TAG] KILL: killed $killed page process(es)"
  if [ "$killed" -gt 0 ]; then
    sleep 3
    step "AFTERKILL" 25 || { shot 5; exit 1; }
    shot 5
    adb logcat -d -b main 2>&1 | tr -d '\r' | grep -iE "geckoview|onKill|onCrash|page process" | tail -8
  else
    note "[$TAG] KILL: no page process found to kill, step skipped"
  fi

  log "close the app completely and open it again: the open tab should come back"
  adbx am force-stop "$PKG"
  sleep 2
  adbx am start -W -n "$PKG/.MainActivity" > /dev/null
  sleep 3
  step "RESTART" 30 || { shot 6; exit 1; }
  shot 6
fi

log "full log for later reading"
adb logcat -d -t 4000 2>&1 | tr -d '\r' > "smoke-logcat-$TAG.txt" || true
if grep -q "FAIL" "$RESULT"; then note "[$TAG] OVERALL: FAIL"; exit 1; fi
note "[$TAG] OVERALL: PASS (on an x86_64 emulator, not on a phone)"
