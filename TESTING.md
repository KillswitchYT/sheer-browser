# Testing Sheer on the phone

Nothing here is automated except the start-and-open-a-link check CI runs on an emulator. Each line says what to look at.

## Updates (the first thing to try)
1. Settings, Updates, Check for updates. If a newer build exists it says "Update now, only X MB".
2. Tap it. The first time Android sends you to a screen to allow Sheer to install updates. Allow it, come back, check again, tap, then confirm Android's prompt.

## Browsing
3. Type `example.com`, then a search like `best pizza`. Back, forward, reload, stop work. A link that opens a new window becomes a new tab.
4. Close the app completely and open it again: your tabs are back.
5. History tab: pages you visited are listed, tap one to open it, the cross deletes one, "Clear history" asks twice.
6. Tabs: "New private tab" keeps nothing: no history, no cookies, not restored. Android hides it from recents and screenshots.

## Look
7. Settings, Appearance: Bars at the top, Page scrolls under the bars, Frosted glass. Check the tab bar text and icons over a bright page and a dark page.

## Privacy
8. Settings, Clear browsing data: history, cookies and site data, cache, and the open site. Each asks for a second tap.
9. Settings, About, Recent stops shows what stopped the app or a page and when.

## Known and unverified
- Whether "Dangerous site warnings" works at all (it needs a Google key inside GeckoView).
- The default-browser role, private tabs leaving nothing behind, and the update install prompt have not been seen on a phone.
- No emulator test beyond start and open a link: the APK is arm64 only and the emulator is x86_64.
