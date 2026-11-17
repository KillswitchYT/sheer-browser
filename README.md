# Sheer

A privacy-first Android browser on the Firefox engine (GeckoView), with the liquid glass look of Aura Alarm.
Native Kotlin and Jetpack Compose, no Material 3. All glass comes from the Kyant0/AndroidLiquidGlass library (copies in `glass/`).

Sheer has no account, no analytics, no ads and no server of its own. The Firefox engine inside also talks to Mozilla for some
services; the full list comes with the privacy audit.

**Read HANDOFF.md first.** It has the state of the project, the rules, how the build and updates work, and what is left to do.

## What it does today (v0.1.15)
Browse (glass address bar, tabs restored after a restart, bars at the top or the bottom, page under the bars or clear of them), Tabs with private tabs,
History (searchable, kept on the phone), Settings (theme, search engine, strict tracking protection, HTTPS only, clear browsing data), and an
in-app updater that downloads a patch of a few MB instead of the whole app.

## Build
GitHub Actions builds the optimized (R8) release APK on every push to `main` and publishes release `latest` with `Sheer.apk`, `update.json` and the update patches.
The signing key lives only in the repo secrets `SHEER_KEYSTORE_B64` and `SHEER_KEYSTORE_PASSWORD`; without them the build refuses to run.
The engine version is pinned in `gradle.properties`. `tools/run-engine-tests.sh` runs 81 checks on a plain JVM (needs kotlinc 2.x and a JDK).

## Credits
Glass: Kyant0/AndroidLiquidGlass 2.0.1 and Kyant0/Shapes 1.2.1 (Apache-2.0, see `third_party/`). Engine: Mozilla GeckoView (MPL 2.0). Font: Plus Jakarta Sans.
