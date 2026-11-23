# Sheer handoff

State when this was written: build **v0.1.15** (versionCode 115), source commit `8e20747` on `main` of https://github.com/KillswitchYT/sheer-browser (public).
The GitHub repo is the source of truth. If it is newer than this file, trust the repo and read `git log`.

## 1. What this is
Sheer is a privacy-first Android browser on Mozilla's Firefox engine (GeckoView), native Kotlin + Jetpack Compose, no Material 3, with the
liquid glass look of the user's other app Aura Alarm (https://github.com/KillswitchYT/aura-alarm, public, the style reference).
Package `com.killswitch.sheer`. Glass comes only from Kyant0/AndroidLiquidGlass 2.0.1 (our fixed copies are in `glass/`).
The user builds nothing locally. Everything compiles on GitHub Actions and is driven from the assistant's sandbox with an API token.

## 2. The user and how to talk to them
- Android phone only (Samsung Galaxy A52, Android 14, 90 Hz), no PC, often on limited 4G. Small updates matter a lot.
- Talk like a real person. No em dashes. Vulgar language is fine. Do NOT just agree: challenge bad ideas, call out bullshit, be honest.
- Never claim something works unless you ran it and saw it succeed. Say plainly what is untested. Separate "verified" from "unverified".
- Replies are read on a phone: lead with the answer, short, plain words, about one or two screens. If they say their head hurts, simplify.
- They do NOT want long CI tests ("what's the point if I'm gonna test it anyway"). Keep the emulator test to start + open a link.
- They are impatient with limits: do the work, do not ask questions you can answer yourself. One question at most, only when needed.
- They send screenshots of the real app. Read them carefully and say what you see before changing anything.
- Their GitHub token is in no file. Never write it into any file, zip, git config or reply (see section 6).

## 3. Rules from the original brief (still apply)
- 100% native Kotlin + Compose. NO Material 3. Foundation plus custom only.
- NO cards. Content sits on the scene, rows split by hairlines (`Modifier.topHairline()`). Containers only for sheets, dialogs, and ONE grouped
  glass block per Settings section.
- One accent for the main action. Real canvas icons (`Ic` in ui/Icons.kt), no emoji. One big title, everything else quiet.
- Every screen has all its states (loading skeleton, empty, error, no-match). Motion small (150 to 400 ms), press springs, staggered fade-in.
- All glass from the library's components in `glass/`. Never write your own blur or glass imitation. Do not re-copy `glass/` from upstream:
  our copies contain fixes (rememberUpdatedState in gesture handlers, slider takes a Float).
- Keep the names `Aura, ts, tsg, pressable, staggerIn, LocalArrival, topHairline, drawAuraScene, glassBackdrop, LocalScene`.
- Theme is ONE process-wide object (`Aura`). Never force a palette for one screen. Every Activity applies the saved theme in onCreate.
- Palettes in `Palettes.kt` with contrast tests in `tools/EngineTest.kt`. Known and disclosed: white text on the library's tinted blue glass is about 2.6:1.
- Honest deviation: the clear-glass tints in `glass/` use `Color.Black/White.copy(alpha)` (the brief says use `Aura.overlay`). Tell the user if it comes up.
- Toolchain (do not change): Gradle 9.7.1, AGP 9.3.2, Kotlin 2.4.10, Compose 1.12.0, activity-compose 1.13.0, core-ktx 1.19.0, backdrop 2.0.1,
  shapes 1.2.1, GeckoView (pinned, below), buildTools 37.0.0, minSdk 26, targetSdk 34, arm64-v8a only. compileSdk is 37.2
  (`compileSdk { version = release(37) { minorApiLevel = 2 } }`), because GeckoView 160 refuses anything older. The R8 release is the app.
- Never touch the user's other repos (bulletproof-alarm, Mini-Compressor, MiniCompressor, cool-keyboard, idk). aura-alarm is read-only reference.

## 4. What exists (v0.1.19)
Tabs: Browse, Tabs, History, Settings (glass bottom tab bar, Kyant animation kept).
- **Browse**: GeckoView page (TextureView backend, so glass can see it) with a glass address bar, back, forward, reload/stop, progress line,
  start page, crash page. Tab restore after restart (saved a moment after every change). New-window links open new tabs. Links from other apps
  open in a new tab (default-browser intent filter present).
- **Tabs**: list, new tab, close, New private tab, Close all private tabs.
- **Private tabs**: GeckoView private mode, no history delegate, never saved, FLAG_SECURE while showing (blocks screenshots).
- **History**: own SQLite store (`History.kt`) fed by GeckoSession.HistoryDelegate (normal tabs only), search, grouped by day, delete one, clear (two taps), visited-link colouring.
- **Settings**: Appearance (theme, "Bars at the top", "Page scrolls under the bars" default on, "Frosted glass" default off, Settings > Performance: "Skip hidden background" default on, "Calm glass while scrolling" default off, "Test: hide the bars while scrolling" default off, "Test: page on a SurfaceView" default off, "Test: run at 60 Hz" default off), Search (Google default,
  DuckDuckGo, Brave Search, Startpage), Privacy (strict tracking protection, HTTPS only, dangerous site warnings), Clear browsing data (history,
  cookies and site data, cache, current site; two taps), Updates, About (version, engine, Recent stops, Smoothness frame meter + reset).
- **Updater** (`Updater.kt`, `Patch.kt`): opt-in "Check for updates" reads `update.json` from the GitHub release `latest`, downloads a ~2.4 MB patch
  (with resume), rebuilds the new APK from the installed APK, checks SHA-256, hands it to PackageInstaller (`InstallReceiver`). Falls back to the
  full 92 MB download only on an explicit tap. Needs REQUEST_INSTALL_PACKAGES (user allows once).
- **Crash tooling**: `CrashLog.kt` writes Java crashes and reads Android's ApplicationExitInfo (also native crashes), shown on next launch with Copy;
  page-process stops auto-reload once (second stop within 30 s shows "This page crashed"); Settings, About, Recent stops shows the log.
- **Engine config** (`Gecko.kt`): strict ETP incl. fingerprinting and cryptominers, cookie isolation, query-parameter stripping, GPC on, HTTPS-only,
  remote debugging and about:config off, safe browsing toggle. Telemetry/remote settings NOT yet audited.
- Permissions in the built APK: INTERNET, REQUEST_INSTALL_PACKAGES (ours); ACCESS_NETWORK_STATE, WAKE_LOCK, MODIFY_AUDIO_SETTINGS (GeckoView's).

Prefs keys (SharedPreferences "sheer"): th, se2 (engine index, Google = 0), https, etc, sb, top (bars at top), under (page under bars), frost2, calm (calm glass, default 0), skiphid (default 1), testhide, cap60, tabs (json), exit_seen.

## 5. Code map (`app/src/main/java/com/killswitch/sheer/`)
- `MainActivity`, `SheerApp` (Application, holds `Browser`, installs CrashLog), `Browser.kt` (BTab/Browser: sessions, delegates, restore/save, recovery, private tabs).
- `Gecko.kt` runtime + live setters. `Settings.kt` prefs. `UrlInput.kt` pure address-bar logic (tested). `History.kt`. `CrashLog.kt`. `Updater.kt`, `Patch.kt` (pure JVM, tested).
- `ui/`: `SheerShell` (scene, persistent tabs, tab bar placement), `BrowseTab`, `TabsTab`, `HistoryTab`, `SettingsTab`, `Theme` (Aura, ts, tsg, scene), `Parts`, `Dialogs`, `Icons`, `UiPrefs` (Compose state for layout choices).
- `glass/`: Kyant copies (LiquidBottomTabs, LiquidField/LiquidPanel, LiquidButton, LiquidToggle, LiquidSlider...). Frosted vs clear look is switched by `UiPrefs.frosted`.
- Layout rules in `BrowseTab`: bars at top = tab bar under the status bar (8 dp gap, 64 dp), address row under it (top 80 dp, 48 dp tall). Page under bars = page fills the whole screen
  (pageTop 0, pageBottom 0). Off = page stops where the bars end and the dark scene shows behind them.
- Performance (v0.1.16 frame meter, profile and loading-bar fix; v0.1.17 calm glass): the page is a TextureView so every Gecko frame while scrolling makes HWUI redo ~6 glass elements (each an offscreen layer replaying the page plus a colour-filter and a lens/dispersion shader pass). On a tile-based GPU (Adreno 618) the render-target switches are the likely cost, not the shader area.
  Measures taken: `FrameMeter.kt` (Window.OnFrameMetricsAvailableListener, Settings > About > Smoothness / Reset smoothness counter; counts the app window only, not Gecko's own compositing);
  `baseline-prof.txt` (our code + Kyant) plus `androidx.profileinstaller` (library profiles were already merged by AGP but a sideloaded app never installs them without ProfileInstaller);
  the loading bar reads its animated progress only in a `drawBehind`, so a page load no longer recomposes the whole Browse screen every frame;
  "Calm glass while scrolling": `GeckoSession.ScrollDelegate` -> `ScrollWatch.moved()` -> `UiPrefs.scrolling` (off 250 ms after the last report); the bars refract `rememberBackdrop(live) { if (!(calm && scrolling)) drawBackdrop() }`, which only reads the state while drawing, so nothing recomposes.
  v0.1.18: every experiment is a toggle in Settings > Performance. "Skip hidden background" does not paint the aura gradient (BrowseTab and the shell's scene layer) while a real page fills the screen (`UiPrefs.browseCovers`, set from BrowseTab). "Test: hide the bars while scrolling" fades the address bar row and tab bar to alpha 0 while `UiPrefs.scrolling` (decides whether glass costs anything at all). "Test: run at 60 Hz" sets `preferredRefreshRate` (Samsung's High motion smoothness may ignore it; the Smoothness report now prints the Hz it ran at).
  FIRST MEASUREMENTS (user's A52, 60 Hz, Smoothness): bars shown avg 31.5 ms, worst 103 ms, 97% of frames slower than one refresh, 25% slower than two. With "Test: hide the bars" avg 31.0 ms, worst 141 ms, 96% / 25%. So the glass DRAWING is not the cost (Calm glass also did nothing). Compose may still re-record the glass nodes every frame (hidden by alpha 0 only stops the GPU), so v0.1.19 splits the frame into UI thread / renderer hand-off / waiting for the screen / GPU in Smoothness, and adds the SurfaceView test switch to separate the TextureView cost from Gecko's own GPU load.
  Researched and NOT built: a shared glass capture (Glasmio's 6 to 17% RenderThread saving came from merging glass pieces into one surface on a heavy page tree; ours is a light tree and the lens only does rounded rects), fusing vibrancy into the lens (needs library internals, ntvien/AndroidLiquidGlass branch perf/vibrancy-in-lens), PixelCopy hybrid (keeps refraction on a SurfaceView but lags the page). The user wants refraction kept: do NOT drop it without being told.
  R8 was already on (`isMinifyEnabled = true`), it does not affect scrolling. Not done on purpose (it removes the live-glass look): SurfaceView page backend. Next lever if scrolling is still bad: a "Fast page" mode (SurfaceView, glass over the static scene).
  Firefox (SurfaceView) scrolls smooth on the user's A52 while Sheer (TextureView + glass) shows ~31 ms frames at 60 Hz, 97% slower than 16.7 ms, and hiding the bars did not change that (but see: nobody confirmed the scroll signal fired, check the bars really vanished). v0.1.19 added the frame-time breakdown in Smoothness and "Test: page on a SurfaceView". First try showed a BLANK page: a SurfaceView must be drawn once, and every glass element replays the page layer in its own offscreen layer, so its position gets clobbered. Fix: in SurfaceView mode the gated backdrops draw nothing (`UiPrefs.fastPage ||` in the `rememberBackdrop` gates in BrowseTab and SheerShell, and the dialog uses `barBackdrop`). CI now builds a third emulator APK with `-PtestFast=true` (`smoke-fast.apk`, tag `fast`) and dumps the app's SurfaceFlinger layers into `smoke-test-fast.log`; look at `smoke-fast-2.png`, `2b`, `3`, `4`.
  SurfaceView mode on the emulator (v0.1.20): still blank AND the app ANRs ("WM sent Transaction ... never received commit callback", the SurfaceView layer is 0x0 with no buffer). Do not spend more on it in this layout; consider removing the toggle. Next idea from the user (verify it): the lag is at FIRST LAUNCH, i.e. Android's compiled code and the Skia shader cache are wiped by every update (we ship constantly) and the phone only recompiles while idle and charging. Verification built: Settings > About > "Compiled code" (size of base.odex/base.vdex, how long ago installed) and `tools/ci/compile-test.sh` (run at the end of smoke-all.sh, output `smoke-test-compile.log` on branch build-logs): A fresh install vs B speed-profile vs C full compile vs D install with a `.dm` (baseline.prof + .profm zipped as primary.prof/.profm, named like the apk) measured by cold start time and `dumpsys gfxinfo` jank. If D compiles at install, the updater (Updater.kt session writes `Sheer.apk`) can also write `Sheer.dm`.
- IMPORTANT: glass bars must never sample the layer they are drawn inside. The Browse page has its own `layerBackdrop` and the bars are drawn OUTSIDE it.
  Doing it wrong crashed the app instantly (native stack overflow in the render thread).

## 6. Build, CI, release
- `.github/workflows/build.yml`, on push to `main` (and manual runs). One run replaces an older one (concurrency).
- Job `build` (about 3 to 4 min): picks GeckoView (pinned in `gradle.properties` `geckoVersion=160.0.20261104181552`; a manual run with input `gecko=latest` looks up the
  newest on Mozilla maven), loads the signing key from repo secrets `SHEER_KEYSTORE_B64` + `SHEER_KEYSTORE_PASSWORD` (build REFUSES to run without them, nothing is ever signed with a public key),
  runs `assembleRelease`, then makes update patches with `tools/ci/release-assets.py` (bsdiff, converted by `mkpatch.py` to our `SHRP1` format) and publishes the release `latest`:
  `Sheer.apk`, `update.json`, `patch-<from>-to-<to>.bin` for the 2 previous builds, `mapping.txt`. The last 3 APKs are kept in release `archive` only to make patches.
  Logs go to branch `build-logs` (`build.log`, `errors.txt`, `gecko.txt`, `run.txt`).
- Job `smoke` (needs build, runs AFTER the release is published, so it never delays the user): x86_64 emulator build, installs, starts, opens a link. Results and screenshots on `build-logs`
  (`smoke-result-bottom.txt`, `smoke-bottom-*.png`). `SMOKE_MODE=full` adds the long page, page-process kill and restart steps. It is not a phone (software graphics, x86_64).
- versionCode = 100 + GitHub run number, versionName 0.1.<run>. The download link: https://github.com/KillswitchYT/sheer-browser/releases/download/latest/Sheer.apk
- Signing fingerprint (SHA-256, must match after every build): `9E:6B:B6:98:2C:E0:1B:D4:EF:30:DF:F2:14:BD:26:BB:68:9F:D7:17:1E:64:93:D5:FB:B8:EE:86:CA:60:44:B4`.
  The keystore and its password are NOT needed in a new chat (CI has them as secrets). The user holds a backup. Never ask for or print them again.
- Scripts: `tools/ci/gate-and-go.sh "message" [seconds]` (token gate, push, wait, read), `cycle.sh`, `read.sh <runid>` (errors from build-logs), `gecko-version.sh`, `smoke-*.sh`,
  `tools/run-engine-tests.sh` (81 JVM checks: palettes, address bar, patch applier; needs kotlinc 2.0.21 in /opt/kt and a JDK).
- Sandbox network works for: github.com, api.github.com, raw.githubusercontent.com, codeload.github.com, release-assets.githubusercontent.com, apt, PyPI, npm, crates.
  It does NOT reach maven.mozilla.org or Google's maven. So you cannot run Gradle here. CI is the only compiler.
- Token rules: user pastes a fine-grained token (repo `KillswitchYT/sheer-browser` only: Actions RW, Contents RW, Metadata R, Secrets RW, Workflows RW).
  Save with `umask 077; echo "export GH_TOKEN='...'" > /tmp/.gh_env`. NEVER write it anywhere else. Gate EVERY push: `grep -rF` for 24 characters from the middle of the real
  token over the tree and `/mnt/user-data/outputs`, abort if found (`gate-and-go.sh` does it). The shell is `sh` (dash): use `bash` for `${var:40:24}`. Remind the user to revoke the token when done.
- Build flakes: "could not resolve plugin" or a Gradle download timeout with no Kotlin errors = network flake. Rerun: `POST /repos/KillswitchYT/sheer-browser/actions/runs/<id>/rerun`.
- After a green build, VERIFY before telling the user: download `Sheer.apk` (retry on 5xx, check size), `aapt dump badging` (package, versionCode, label, not debuggable), `apksigner verify --print-certs`
  (fingerprint above), grep `classes.dex` for strings that exist only in the new build, and check the patch rebuilds the new APK byte for byte from the previous APK with `tools/PatchRealTest.kt`.
  (apt: aapt apksigner zipalign bsdiff zstd; pip: pynacl --break-system-packages.)
- Read compile errors with `tools/ci/read.sh <runid>`; `grep "^e: " /tmp/ci/build.log`. The local `kotlinc` parse check only catches SYNTAX errors, never missing imports or wrong signatures.
  Read your own diff for imports, parameter order, mixed-type `arrayOf` (use `arrayOf<Any>`), and shadowed names. Check GeckoView names against Mozilla's source mirror
  `https://raw.githubusercontent.com/mozilla-firefox/firefox/main/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/<File>.java` (main is a few weeks ahead of the pinned 160).

## 7. Verified vs unverified
Verified (seen working): CI builds; signed APK installs over the previous; launch no longer crashes (emulator + the user's phone); Wikipedia/Google render in the app; top layout in the emulator;
tab restore after restart; page-process kill auto-recovers (emulator); patch rebuilds the next APK byte for byte (desktop JVM, real APKs); the user saw the glass, bars and history tab on their phone.
UNVERIFIED on a phone (the user has not reported): the in-app updater end to end incl. Android's install prompt and the "install unknown apps" screen; default-browser role; History recording and visited
colouring; private tabs leaving nothing behind (relies on the engine's private mode) and FLAG_SECURE; Clear browsing data; "Dangerous site warnings" (needs a Google key inside GeckoView, unconfirmed);
the cause of the earlier "This page crashed" (unknown; auto-recovery and Recent stops exist; ask for a screenshot of Settings, About, Recent stops if it recurs).

## 8. Decisions and why
- Engine is PINNED. Firefox now ships every 2 weeks (since Sept 2026). A new engine makes the next update big (patch size unmeasured), and it is a security trade-off. Bump only when the user asks and is on Wi-Fi: run the workflow with `gecko=latest`, or edit the pin.
- Google is the default search engine at the user's request (Settings notes Google can log searches). `se2` key so the default is Google for existing installs.
- Page insets are Compose padding, not Gecko's dynamic toolbar API (its behaviour for bottom bars was unclear and content was hiding under bars).
- QWEA0/Liquid-Glass-Android was considered and NOT used: it is a View-system library whose README says not to use it from Compose (do not wrap it in AndroidView), it has native code for arm64/armv7 only,
  and it would mean rebuilding the page and address bar as Views. Instead the Kyant glass is tuned to look like its demo: clear glass = no blur, strong lens at the rim (16/32 dp with depth and chromatic aberration),
  tint about 17% black in dark and 5% black in light, shadowed text (`tsg`). "Frosted glass" in Settings is the readable blurred alternative.
- The moving bubble in the tab bar keeps a gentle lens (5/8 dp, no colour fringes) so icons and labels do not smear.
- No auto update checks. The updater only touches the network when the user taps. GitHub sees their IP.
- Bitwarden: use the Bitwarden browser extension from AMO once extensions and toolbar popups exist. A native Bitwarden client is post-v1: the SDK `sdk-internal` is GPL v3 or the Bitwarden SDK license, Sheer would have to be GPL v3,
  `com.bitwarden:sdk-android` is on GitHub Packages and needs a CLASSIC token with read:packages, Bitwarden wants unofficial clients to register a client identifier. Gecko is MPL 2.0.
- Native messaging only works for privileged built-in extensions talking to the app, so extensions that need a desktop companion stay broken.

## 9. Known issues and open threads
- Missing basics, in progress (user said "work on the rest of the features", order: prompts, then page actions, then fullscreen + bookmarks). DONE in v0.1.23 (unverified on the phone): `PageDelegates.kt` (`PagePrompts`: alert, confirm, prompt, before-unload, popup, repost, `<select>` lists, date/time and colour typed fallbacks, file chooser; `PagePermissions`: location/camera/microphone asked each time, notifications/tracking/persistent storage/third-party storage/loud autoplay refused silently), `ui/AskDialog.kt` (`Ask`, `PageAsks` queue, glass `AskDialog` shown in SheerShell), launchers in MainActivity (`browser.filePicker`, `browser.askAndroid`), manifest now also declares CAMERA, RECORD_AUDIO, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION (asked from Android only when a site wants them; disclosed in "What Sheer collects"). Not done yet: HTTP auth prompt, remembering a site's answer, native date/time/colour pickers. Page actions (v0.1.24, same session): `Downloads.kt` (onExternalResponse -> MediaStore Downloads, images via GeckoWebExecutor, toasts), `PageActions.kt` (three-dot page menu: Share / Find in page / Copy link; long-press menu on links and images through `onContextMenu`), find bar that replaces the address bar (`finding` in BrowseTab, `session.finder`), `Ic.More`. Verified on the CI emulator (screenshots in build-logs, not on the phone): alert, confirm, select list, location, camera+microphone, popup questions all appear in the glass dialog and the page gets the answer; page menu, find (1/5 counter, highlight), long-press link and image menus, Copy link, Save image and a download all work and the files land in Downloads. KNOWN BUG: a text prompt (`prompt()`) answered with OK left the page waiting; v0.1.26 logs `SheerPrompt` lines (asking/answered/took it back) and no longer auto-focuses the field (hypothesis: moving keyboard focus off GeckoView makes Gecko drop the prompt); the feature test has `prompt` and `promptfield` runs to tell. Fullscreen video (v0.1.26): `onFullScreen` -> `UiPrefs.fullscreen`, both bars hidden, system bars hidden + landscape via `browser.setImmersive`, back exits (`exitFullScreen`). NEXT: bookmarks, then extensions. `tools/ci/feature-test.sh` (run by smoke-all.sh, pages in `tools/ci/pages/`) opens test pages that trigger alert/confirm/prompt/select/location/camera/popup/file, taps the answer in Sheer's dialog through uiautomator and screenshots before/after to `smoke-feat-*.png` on branch build-logs (read them, it is not pass/fail). The SurfaceView test switch and its CI build were removed (it blanked the page and froze the app).
- Scrolling lag reported by the user (v0.1.15). v0.1.16 added the frame meter and the ProfileInstaller; v0.1.17 added "Calm glass while scrolling". Neither is measured on the phone yet. Ask the user for the Smoothness numbers with the toggle on and off (and Frosted on and off) before changing more.
- Last open question to the user: text and icons on the clear-glass bars are weak on bright pages in dark theme. Options offered: raise the tint more, or make label colour follow what is behind. They asked for "5% darker", done (v0.1.14). Wait for feedback.
- With bars at the top and "Page scrolls under the bars" on, the top of the page starts under the bars (visible through the glass, not tappable until scrolled). Status bar icons sit on the page.
- Basic browser features that are still MISSING (important, tell the user honestly): file downloads (`onExternalResponse` unhandled), site permission prompts (camera, mic, location, notifications),
  JS alert/confirm/prompt dialogs and file chooser (`PromptDelegate`), fullscreen video (`onFullScreen`), find in page, long-press context menu (open in new tab, copy link, save image), share, bookmarks,
  external schemes (tel:, mailto:, intent:), request desktop site, text size, password autofill (Bitwarden extension is the plan).
- Extensions slice NOT started. Plan: install from an AMO link or .xpi, permission prompt (WebExtensionController.PromptDelegate), list, enable/disable/remove, toolbar popups via `WebExtension.ActionDelegate`
  (return a GeckoSession per popup), bundled uBlock Origin (GPL v3; `installBuiltIn` gives extra privileges, the alternative is installing it from AMO on first run; its only network use is filter list updates).
- Privacy audit slice NOT done: list exactly what Gecko still contacts (Mozilla services, remote settings, safe browsing lists from Google), lock prefs via a GeckoView config yaml (`configFilePath`), verify, then state it plainly. Do not claim "collects nothing" beyond "Sheer sends nothing to us".
- README.md and TESTING.md are only partly current.

## 10. Lessons already paid for
- Glass layer self-sampling crash (section 5). TextureView is used so the glass can see the page; a SurfaceView would not be captured and ignores parent alpha.
- GeckoView content processes are named `<pkg>:sandboxed_tab_...`, not `:tab`.
- `resValue` needs `buildFeatures { resValues = true }`. `statusBars` needs its own import. Kotlin 2.4 rejects `arrayOf(String, Long)` as an intersection type.
- Tabs were saved only on stop, so a kill lost them. Now saved ~1.5 s after every change.
- Release assets can return 5xx: retry. raw.githubusercontent.com caches about 5 minutes: use the API contents endpoint with `?ref=` for fresh files. A new push cancels the run in progress.
- Docs-only commits: put `[skip ci]` in the message so no build or release is made. Do not push while a run you care about is in progress.

## 11. Suggested next steps (ask the user, recommend basics first)
1. Get feedback on v0.1.15 (bars at top, glass readability, updater flow).
2. Missing basics: downloads, permission prompts + JS dialogs + file chooser, fullscreen video, find in page, long-press menu, share. Then bookmarks.
3. Extensions + toolbar popups + uBlock Origin + Bitwarden extension.
4. Privacy audit and honest "what Sheer still connects to" list.
5. Engine bump when the user is on Wi-Fi and asks. Post-v1: native Bitwarden client.
