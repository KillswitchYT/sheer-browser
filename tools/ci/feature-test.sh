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
# Where is the node with this exact text or description? (Sheer's dialogs are Compose, they show up in the accessibility dump.)
# The dump fails now and then while something is animating, so it is tried up to three times.
where() {
  local try
  for try in 1 2 3; do
    rm -f ui.xml; adbx uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
    python3 tools/ci/where.py "$1" ui.xml ${2:-} && return 0
    sleep 1
  done
  return 1
}
# The emulator is slow and Android sometimes puts "Pixel Launcher isn't responding" over everything: press Wait so it does not hide the screen.
clear_anr() { local pos; if pos=$(python3 tools/ci/where.py "Wait" ui.xml 2>/dev/null); then adbx input tap $pos > /dev/null; sleep 1; fi; }
tap_text() { local pos; if pos=$(where "$1" ${2:-}); then echo "   tap \"$1\" at $pos"; adbx input tap $pos > /dev/null; else echo "   NO node with the text \"$1\""; fi; }

one() { # name, page, [tap-the-screen-first], then the texts to tap
  local name="$1" page="$2" first="$3"; shift 3
  echo; echo "=== $name"
  adbx am force-stop "$PKG"; adbx pm clear "$PKG" > /dev/null; grant; adb logcat -c; sleep 1
  adbx am start -a android.intent.action.VIEW -d "http://localhost:8765/$page" -n "$PKG/.MainActivity" > /dev/null
  sleep 9
  adbx uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1; clear_anr
  case "$first" in
    tap)  adbx input tap 540 1200 > /dev/null; sleep 2 ;;
    hold) adbx input swipe 540 1200 540 1200 1300 > /dev/null; sleep 2 ;;
  esac
  shot "$name-1"
  for t in "$@"; do
    case "$t" in
      type:*) adbx input text "${t#type:}" > /dev/null; sleep 3 ;;
      key:*)  adbx input keyevent "${t#key:}" > /dev/null; sleep 1.5 ;;
      xy:*)   adbx input tap ${t#xy:} > /dev/null; sleep 2 ;;
      swipe:*) adbx input swipe 540 1800 540 700 350 > /dev/null; sleep 1.5 ;;
      last:*) tap_text "${t#last:}" last; sleep 2 ;;
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
one prompttick prompttick.html no OK wait:3
one promptfieldtick prompttick.html no Sam OK wait:2
one bookmark menu.html no "Page menu" "Add bookmark" last:History Bookmarks
one fullscreen fullscreen.html tap "Got it" key:KEYCODE_BACK wait:2
one adblock    adblock.html  no   wait:11
one extpopup   menu.html     no   "Page menu" "uBlock Origin" wait:5
one extlist    menu.html     no   last:Settings swipe:up wait:1
one menu     menu.html     no   "Page menu" "Find in page" type:banana wait:2
one linkmenu link.html     hold "Copy link"
one imagemenu image.html   hold "Save image" wait:3
one download download.html tap  wait:4
echo; echo "=== what ended up in Downloads"; adbx ls -l /sdcard/Download/
echo; echo "=== ANR reports of Sheer (what the main thread was doing)"
adbx dumpsys dropbox --print data_app_anr 2>&1 | tr -d '\r' > anr-dump.txt
grep -c "Process: $PKG" anr-dump.txt | sed 's/^/   reports: /'
awk -v p="$PKG" '/^Process: /{show=($2==p)} show' anr-dump.txt | grep -n -E "^Process:|^Subject:|Reason|\"main\" prio|^  at |Cpu usage|CPU usage|TOTAL|Load:|^ +[0-9.]+% [0-9]+/" | head -120
echo; echo "=== memory and CPU right now"
adbx cat /proc/meminfo | head -4
adbx top -b -n 1 -m 10 2>&1 | head -16
adbx dumpsys meminfo "$PKG" 2>&1 | grep -E "TOTAL|Native Heap|Dalvik Heap" | head -4
kill $SRV 2>/dev/null
echo; echo "feature test finished"
