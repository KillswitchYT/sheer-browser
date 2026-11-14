#!/usr/bin/env bash
# The "fake phone" smoke test. CI runs it on an Android emulator against the OPTIMIZED apk (the x86_64 build of the same code).
#   tools/ci/smoke-test.sh <apk> <package>
# It does what a person does: installs the app, opens it, then opens a web link, and checks the app is still alive each time.
# It saves the phone's crash log, a screenshot after each step, and a one-line verdict per step in smoke-result.txt.
# HONEST LIMITS: an emulator is not a Samsung, it uses software graphics, and this build is x86_64 while the real app is arm64.
# A crash here is real. A pass here does not prove the phone is fine.
set -u
APK="${1:?need the apk path}"; PKG="${2:?need the package name}"
RESULT="${RESULT:-smoke-result.txt}"; LOG="${LOG:-smoke-test.log}"
: > "$RESULT"; : > "$LOG"
exec > >(tee -a "$LOG") 2>&1

log()  { echo "[$(date +%T)] $*"; }
note() { echo "$*" | tee -a "$RESULT"; }
adbx() { adb shell "$@" 2>&1 | tr -d '\r'; }
alive() { [ -n "$(adbx pidof "$PKG")" ]; }
shot() { adb exec-out screencap -p > "smoke-$1.png" 2>/dev/null || true; }

# The lines of the phone's log that say an app died, from every buffer.
crash_lines() {
  adb logcat -d -b crash 2>&1 | tr -d '\r' | tail -80
  adb logcat -d -b main -b system 2>&1 | tr -d '\r' \
    | grep -E "FATAL EXCEPTION|Fatal signal|JNI DETECTED ERROR|AndroidRuntime|SIGSEGV|SIGABRT|has died|Process .*$PKG.* ended|ANR in $PKG|Force finishing activity $PKG|java\.lang\.[A-Za-z]*(Error|Exception)" \
    | tail -80
}

# Wait up to $1 seconds, return early (1) as soon as the app is gone.
hold() {
  local i
  for i in $(seq 1 "$1"); do
    alive || return 1
    sleep 1
  done
  return 0
}

step() { # name, seconds to watch
  local name="$1" secs="$2"
  if hold "$secs"; then
    note "$name: PASS the app was still running after $secs s"
    return 0
  fi
  note "$name: FAIL the app died within $secs s"
  log "---- crash log"
  crash_lines | tee smoke-crash.txt
  return 1
}

log "emulator: $(adbx getprop ro.product.cpu.abi) android $(adbx getprop ro.build.version.release) api $(adbx getprop ro.build.version.sdk)"
adb logcat -c

out=$(adb install -r "$APK" 2>&1); echo "$out"
if ! echo "$out" | grep -q "Success"; then note "INSTALL: FAIL $out"; exit 1; fi
note "INSTALL: PASS"

adbx pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

log "cold start"
adbx am start -W -n "$PKG/.MainActivity"
sleep 3
if step "START" 30; then
  shot 1
  log "open a web link, the way another app would"
  adbx am start -W -a android.intent.action.VIEW -d "https://example.com" -n "$PKG/.MainActivity"
  if step "LINK" 30; then
    shot 2
    log "what is on top now"
    adbx dumpsys activity activities | grep -E "ResumedActivity" | head -2
    adb logcat -d -b main -b system 2>&1 | tr -d '\r' | grep -E "Gecko|geckoview|example.com" | tail -15
  else
    shot 2
  fi
else
  shot 1
fi

log "full log for later reading"
adb logcat -d -t 4000 2>&1 | tr -d '\r' > smoke-logcat.txt || true
if grep -q "FAIL" "$RESULT"; then note "OVERALL: FAIL"; exit 1; fi
note "OVERALL: PASS (on an x86_64 emulator, not on a phone)"
