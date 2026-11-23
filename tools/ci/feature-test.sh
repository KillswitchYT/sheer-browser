#!/usr/bin/env bash
# Opens small test pages that make the page ask for something (an alert, a <select>, location...), taps the answer in Sheer's own dialog and
# takes a screenshot before and after, so the screenshots show that the question appeared and that the page got the answer back.
#   tools/ci/feature-test.sh <apk> <package>
# Not a pass/fail test: the screenshots (smoke-feat-*.png) are read by a person. An emulator is not a Samsung.
set -u
APK="${1:?need the apk path}"; PKG="${2:?need the package name}"
LOG="smoke-test-features.log"; : > "$LOG"
exec > >(tee -a "$LOG") 2>&1
adbx() { adb shell "$@" 2>&1 | tr -d '\r'; }
python3 -m http.server 8765 --bind 127.0.0.1 --directory tools/ci/pages > /dev/null 2>&1 &
SRV=$!
adbx settings put global hide_error_dialogs 1 > /dev/null
adb reverse tcp:8765 tcp:8765 > /dev/null 2>&1   # localhost counts as a secure place, so location and camera can be asked for
adb install -r "$APK" 2>&1 | tail -1
grant() { for p in ACCESS_FINE_LOCATION CAMERA RECORD_AUDIO; do adbx pm grant "$PKG" "android.permission.$p" > /dev/null; done; }  # skip Android's own box, the test is Sheer's

shot() { adb exec-out screencap -p > "smoke-feat-$1.png" 2>/dev/null; echo "   screenshot smoke-feat-$1.png"; }
# Where is the node with this exact text? (Sheer's dialogs are Compose, they show up in the accessibility dump.)
where() {
  adbx uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try: root = ET.parse('ui.xml').getroot()
except Exception: sys.exit(1)
for n in root.iter('node'):
    if n.get('text') == sys.argv[1] or n.get('content-desc') == sys.argv[1]:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.get('bounds'))
        print((int(m[1]) + int(m[3])) // 2, (int(m[2]) + int(m[4])) // 2); sys.exit(0)
sys.exit(1)
PY
}
tap_text() { local pos; if pos=$(where "$1"); then echo "   tap \"$1\" at $pos"; adbx input tap $pos > /dev/null; else echo "   NO node with the text \"$1\""; fi; }

one() { # name, page, [tap-the-screen-first], then the texts to tap
  local name="$1" page="$2" first="$3"; shift 3
  echo; echo "=== $name"
  adbx am force-stop "$PKG"; adbx pm clear "$PKG" > /dev/null; grant; adb logcat -c; sleep 1
  adbx am start -a android.intent.action.VIEW -d "http://localhost:8765/$page" -n "$PKG/.MainActivity" > /dev/null
  sleep 9
  case "$first" in
    tap)  adbx input tap 540 1200 > /dev/null; sleep 2 ;;
    hold) adbx input swipe 540 1200 540 1200 1300 > /dev/null; sleep 2 ;;
  esac
  shot "$name-1"
  for t in "$@"; do
    case "$t" in
      type:*) adbx input text "${t#type:}" > /dev/null; sleep 3 ;;
      key:*)  adbx input keyevent "${t#key:}" > /dev/null; sleep 1.5 ;;
      wait:*) sleep "${t#wait:}" ;;
      *) tap_text "$t"; sleep 1.5 ;;
    esac
  done
  shot "$name-2"
  adb logcat -d -s SheerPrompt:W 2>&1 | tr -d '\r' | sed 's/^/   log: /' | tail -8
}
one alert    alert.html    no   OK
one confirm  confirm.html  no   OK
one prompt   prompt.html   no   OK
one select   select.html   tap  Banana
one geo      geo.html      no   Allow wait:5
one media    media.html    no   Allow wait:4
one popup    popup.html    no   Allow
one file     file.html     tap
one promptfield prompt.html no Sam OK
one fullscreen fullscreen.html tap key:KEYCODE_BACK wait:2
one menu     menu.html     no   "Page menu" "Find in page" type:banana wait:2
one linkmenu link.html     hold "Copy link"
one imagemenu image.html   hold "Save image" wait:3
one download download.html tap  wait:4
echo; echo "=== what ended up in Downloads"; adbx ls -l /sdcard/Download/
kill $SRV 2>/dev/null
echo; echo "feature test finished"
