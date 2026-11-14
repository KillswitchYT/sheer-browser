# Sheer

A privacy-first Android browser on the Firefox engine (GeckoView), with the same liquid glass look as Aura Alarm.
Native Kotlin and Jetpack Compose, no Material 3. All glass comes from the Kyant0/AndroidLiquidGlass library (copies in `glass/`).

Sheer has no account, no analytics, no ads and no server of its own. The Firefox engine inside also talks to Mozilla for some
services; the full list comes with the privacy audit slice.

## Status: slice 1 (page loads, glass chrome)
- Browse tab: the page, a glass address bar, back, forward, reload and stop. The page is drawn through a TextureView so the glass can see it.
- Tabs tab: open pages, new tab, close. Tabs are restored on the next start (address and title only, pages load when opened).
- Settings tab: theme, search engine, strict tracking protection, HTTPS only, dangerous site warnings.
- Links from other apps open here once Sheer is chosen as the default browser.

## Plan
1. Page loads and the glass test (this slice)  2. Tabs polish  3. Chrome  4. Extensions  5. Toolbar popups (for Bitwarden)
6. uBlock Origin and tracking defaults  7. Privacy audit and a CI job that bumps GeckoView  8. After v1: native Bitwarden client

## Build
GitHub Actions builds the optimized (R8) release APK on every push to `main` and publishes it as release `latest`.
The signing key lives only in the repo secrets `SHEER_KEYSTORE_B64` and `SHEER_KEYSTORE_PASSWORD`; without them the build refuses to run.
The newest stable GeckoView on Mozilla maven is looked up at build time (`tools/ci/gecko-version.sh`) and printed in the run summary.
`tools/run-engine-tests.sh` runs the palette and address bar tests on a plain JVM (needs kotlinc 2.x and a JDK).

## Credits
Glass: Kyant0/AndroidLiquidGlass 2.0.1 and Kyant0/Shapes 1.2.1 (Apache-2.0, see `third_party/`). Engine: Mozilla GeckoView (MPL 2.0).
Font: Plus Jakarta Sans.
