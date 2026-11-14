# Testing Sheer on the phone

Nothing below has been run on a device yet. Each line says what to look at.

## First, the glass test (decides how the bars look)
1. Open any long page (a Wikipedia article). Scroll so text passes behind the address bar and the bottom tab bar.
   - Text behind the bars looks blurred and refracted: glass over the live page works.
   - The bars look like flat tinted pills and the text just vanishes behind them: the page is not captured, tell me and bars go solid.
2. Screenshot it, light and dark.

## Engine
3. Type `example.com`, then a search like `best pizza`. Both load. Progress line shows at the top while loading.
4. Back, forward, reload, stop work. Back button on the phone goes back a page, then leaves the app.
5. Open a link that opens a new window (target=_blank). It becomes a new tab.
6. Tabs tab: new tab, close a tab, close the last tab (a fresh start page appears). Force-stop the app and reopen: tabs are back.
7. Tap a web link in another app, choose Sheer. It opens in a new tab. (Settings, Default apps, Browser app, pick Sheer.)

## Look and feel
8. Switching tabs slides and fades like Aura Alarm. Keyboard opens over the address bar without hiding it.
9. Settings: switch theme Match phone / Dark / Light. Bars and web pages follow. Toggle each privacy switch, they move smoothly.

## What the APK asks for (checked from the built file)
GeckoView adds its own permissions on top of INTERNET. The real list is printed from `aapt dump badging` when a build is verified.

## Known and unverified
- Whether the glass blurs the live page (test 1).
- Whether "Dangerous site warnings" works at all: it needs a Google key inside GeckoView, not confirmed.
- No emulator test: the APK is arm64 only and the CI emulator is x86_64.
- R8 keep rules for GeckoView are a guard, not proven.
