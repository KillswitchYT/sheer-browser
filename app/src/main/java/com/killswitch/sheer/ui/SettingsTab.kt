package com.killswitch.sheer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.sheer.Browser
import com.killswitch.sheer.CrashLog
import com.killswitch.sheer.FrameMeter
import com.killswitch.sheer.History
import org.mozilla.geckoview.StorageController
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.killswitch.sheer.Gecko
import com.killswitch.sheer.Settings
import com.killswitch.sheer.UrlInput
import com.killswitch.sheer.Updater
import com.killswitch.sheer.glass.LiquidPanel
import com.killswitch.sheer.glass.LiquidToggle

private val THEMES = listOf("Match phone", "Dark", "Light")

/** One grouped block: a panel of library glass behind the rows of one topic, rows split by hairlines. */
@Composable
private fun Block(content: @Composable ColumnScope.() -> Unit) {
    LiquidPanel(glassBackdrop(), Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), content = content)
}

/** A heading and one quiet line, then the block of that section. */
@Composable
private fun Section(index: Int, seen: MutableSet<Any>, title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().staggerIn(index, seen.add("set-sec-$index")), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(start = 4.dp, top = 6.dp)) {
            BasicText(title, style = ts(19f, weight = FontWeight.ExtraBold))
            BasicText(sub, style = ts(13f, Aura.Muted))
        }
        Block(content)
    }
}

/** One choice out of a few. The chosen one shows a check. */
@Composable
private fun Choice(title: String, sub: String?, selected: Boolean, first: Boolean, onClick: () -> Unit) {
    val sel by selectAnim(selected)
    Row(
        Modifier.fillMaxWidth().pressable(onClick).then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = ts(16f, weight = FontWeight.Bold))
            if (sub != null) BasicText(sub, style = ts(13f, Aura.Muted))
        }
        Icon(Ic.Check, Aura.AccentText, Modifier.size(22.dp).graphicsLayer { alpha = sel; scaleX = .6f + .4f * sel; scaleY = .6f + .4f * sel })
    }
}

/** A name, one quiet line, and a library toggle. */
@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, first: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = ts(16f, weight = FontWeight.Bold))
            BasicText(sub, style = ts(13f, Aura.Muted))
        }
        Spacer(Modifier.size(14.dp))
        LiquidToggle(selected = { on }, onSelect = onChange, backdrop = glassBackdrop())
    }
}

@Composable
private fun ValueRow(label: String, value: String, first: Boolean) {
    Row(
        Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = ts(16f, weight = FontWeight.Bold))
        BasicText(value, style = ts(15f, Aura.Muted))
    }
}

@Composable
private fun LinkRow(icon: Ic, title: String, first: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, Aura.Muted, Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        BasicText(title, Modifier.weight(1f), style = ts(16f, weight = FontWeight.Bold))
        Icon(Ic.Chevron, Aura.Muted, Modifier.size(18.dp))
    }
}

/** A row that does something you cannot undo: the first tap asks, the second does it. It forgets the question after four seconds. */
@Composable
private fun ClearRow(title: String, sub: String, first: Boolean, action: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) { if (confirm) { delay(4000); confirm = false } }
    LaunchedEffect(done) { if (done) { delay(2500); done = false } }
    Column(
        Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.topHairline())
            .pressable { if (confirm) { action(); confirm = false; done = true } else confirm = true }.padding(vertical = 14.dp),
    ) {
        BasicText(
            if (done) "Cleared" else if (confirm) "Tap again to confirm" else title,
            style = ts(16f, if (confirm) Aura.Rose else if (done) Aura.Mint else Aura.Text, FontWeight.Bold),
        )
        BasicText(sub, style = ts(13f, Aura.Muted))
    }
}

/** A quiet line of status text inside a block. */
@Composable
private fun StatusRow(text: String, first: Boolean) {
    Column(Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp)) {
        BasicText(text, style = ts(15f, Aura.Muted))
    }
}

/** What an update is doing right now, with a thin bar. */
@Composable
private fun ProgressRow(label: String, progress: Float, first: Boolean) {
    Column(
        Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.topHairline()).padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(label, style = ts(15f, weight = FontWeight.Bold))
        Box(Modifier.fillMaxWidth().height(4.dp).background(Aura.overlay(.14f), RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Aura.Accent, RoundedCornerShape(2.dp)))
        }
    }
}

/** The rows of the Updates block, one set for each thing the updater can be doing. */
@Composable
private fun UpdateRows() {
    val ctx = LocalContext.current
    when (val s = Updater.state) {
        is Updater.State.Idle -> LinkRow(Ic.Reload, "Check for updates", true) { Updater.check(ctx) }
        is Updater.State.Checking -> StatusRow("Checking GitHub...", true)
        is Updater.State.UpToDate -> {
            StatusRow("You have the newest version (${s.version}).", true)
            LinkRow(Ic.Reload, "Check again", false) { Updater.check(ctx) }
        }
        is Updater.State.Available -> {
            ValueRow("Newest build", s.manifest.name, true)
            if (s.patch != null) {
                LinkRow(Ic.Check, "Update now, only ${Updater.mb(s.patch.size)}", false) { Updater.update(ctx, s, false) }
            } else {
                StatusRow("There is no small update for your version.", false)
                LinkRow(Ic.Alert, "Download the whole app, ${Updater.mb(s.manifest.apkSize)}", false) { Updater.update(ctx, s, true) }
            }
        }
        is Updater.State.Working -> ProgressRow(s.label, s.progress, true)
        is Updater.State.NeedPermission -> {
            StatusRow("Allow Sheer to install updates in the screen that opened, come back, then check again.", true)
            LinkRow(Ic.Reload, "Check for updates", false) { Updater.check(ctx) }
        }
        is Updater.State.Confirming -> StatusRow("Confirm the update in Android's own prompt.", true)
        is Updater.State.Failed -> {
            StatusRow(s.message, true)
            LinkRow(Ic.Reload, "Try again", false) { Updater.check(ctx) }
        }
    }
}

@Composable
fun SettingsTab(browser: Browser, pad: PaddingValues, seen: MutableSet<Any>, onNotice: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val sysDark = isSystemInDarkTheme()
    var theme by remember { mutableIntStateOf(Settings.theme(ctx)) }
    var engine by remember { mutableIntStateOf(Settings.engine(ctx)) }
    var strict by remember { mutableStateOf(Settings.strictTracking(ctx)) }
    var https by remember { mutableStateOf(Settings.httpsOnly(ctx)) }
    var safe by remember { mutableStateOf(Settings.safeBrowsing(ctx)) }
    var top by remember { mutableStateOf(UiPrefs.addressTop) }
    var under by remember { mutableStateOf(UiPrefs.pageUnder) }
    var frosted by remember { mutableStateOf(UiPrefs.frosted) }
    var calm by remember { mutableStateOf(UiPrefs.calmGlass) }
    var skipHid by remember { mutableStateOf(UiPrefs.skipHidden) }
    var testHide by remember { mutableStateOf(UiPrefs.testHide) }
    var cap60 by remember { mutableStateOf(UiPrefs.cap60) }
    var fastPage by remember { mutableStateOf(UiPrefs.fastPage) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Column(Modifier.staggerIn(0, seen.add("set-head"))) { PageTitle("Settings", "Kept on this phone only") } }

        item {
            Section(1, seen, "Appearance", "Theme, and where the bars sit.") {
                THEMES.forEachIndexed { i, name ->
                    Choice(name, null, theme == i, i == 0) {
                        theme = i
                        Settings.setInt(ctx, "th", i)
                        Aura.apply(i, sysDark)
                        Gecko.applyColorScheme(ctx)
                    }
                }
                ToggleRow("Bars at the top", "The tab bar goes to the very top and the address bar sits under it.", top, false) {
                    top = it; Settings.flag(ctx, "top", it); UiPrefs.addressTop = it
                }
                ToggleRow("Frosted glass", "Blurs and tints the bars so their text stays readable on any page. Off is clear glass.", frosted, false) {
                    frosted = it; Settings.flag(ctx, "frost2", it); UiPrefs.frosted = it
                }
                ToggleRow("Page scrolls under the bars", "The glass blurs the page. The end of a page can hide behind the bars.", under, false) {
                    under = it; Settings.flag(ctx, "under", it); UiPrefs.pageUnder = it
                }
            }
        }

        item {
            Section(2, seen, "Performance", "Switches for finding what makes scrolling heavy. Then Smoothness in About shows the result.") {
                ToggleRow("Skip hidden background", "Does not paint the glass background where a page already covers the whole screen. Nothing changes on screen.", skipHid, true) {
                    skipHid = it; Settings.flag(ctx, "skiphid", it); UiPrefs.skipHidden = it
                }
                ToggleRow("Calm glass while scrolling", "While a page moves, the bars stop bending it and bring the bend back when it rests.", calm, false) {
                    calm = it; Settings.flag(ctx, "calm", it); UiPrefs.calmGlass = it
                }
                ToggleRow("Test: hide the bars while scrolling", "The bars disappear while a page moves. Only to see whether the glass costs anything.", testHide, false) {
                    testHide = it; Settings.flag(ctx, "testhide", it); UiPrefs.testHide = it
                }
                ToggleRow("Test: page on a SurfaceView", "The engine draws faster, but the bars cannot bend the page. If the page goes blank, turn it off.", fastPage, false) {
                    fastPage = it; Settings.flag(ctx, "fastpage", it); UiPrefs.fastPage = it
                }
                ToggleRow("Test: run at 60 Hz", "Asks Android for 60 Hz. Some phones ignore it, Smoothness shows what ran.", cap60, false) {
                    cap60 = it; Settings.flag(ctx, "cap60", it); UiPrefs.cap60 = it
                    (ctx as? android.app.Activity)?.let { a -> applyRefreshCap(a, it) }
                }
            }
        }

        item {
            Section(3, seen, "Search", "What the address bar uses for anything that is not a web address.") {
                UrlInput.ENGINES.forEachIndexed { i, e ->
                    Choice(e.name, if (e.name == "Google") "Google can log what you search." else null, engine == i, i == 0) {
                        engine = i; Settings.setEngine(ctx, i)
                    }
                }
            }
        }

        item {
            Section(4, seen, "Privacy", "Applied to every page, right away.") {
                ToggleRow("Strict tracking protection", "Blocks trackers, fingerprinting and crypto miners.", strict, true) {
                    strict = it; Settings.flag(ctx, "etp", it); Gecko.applyTracking(ctx)
                }
                ToggleRow("HTTPS only", "Refuses pages that cannot be loaded securely.", https, false) {
                    https = it; Settings.flag(ctx, "https", it); Gecko.applyHttps(ctx)
                }
                ToggleRow("Dangerous site warnings", "A list of bad sites, checked on the phone.", safe, false) {
                    safe = it; Settings.flag(ctx, "sb", it); Gecko.applySafeBrowsing(ctx)
                }
            }
        }

        item {
            Section(5, seen, "Clear browsing data", "History is in the History tab. Cookies can be removed here but not listed.") {
                val gc = { Gecko.runtime(ctx).storageController }
                ClearRow("Clear history", "Every page in the History tab.", true) { History.clear(ctx) }
                ClearRow("Clear cookies and site data", "Logs you out of sites and removes what they stored.", false) {
                    gc().clearData(StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.AUTH_SESSIONS)
                }
                ClearRow("Clear the cache", "Saved copies of pages and images. Pages load slower once.", false) {
                    gc().clearData(StorageController.ClearFlags.ALL_CACHES)
                }
                val host = UrlInput.host(browser.current?.url.orEmpty()).takeIf { browser.current?.incognito != true && it.isNotEmpty() && !it.contains(':') }
                if (host != null) {
                    ClearRow("Clear data for $host", "Cookies and site data for the open page only.", false) {
                        gc().clearDataFromHost(host, StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.AUTH_SESSIONS)
                    }
                }
            }
        }

        item {
            Section(6, seen, "Updates", "Only when you tap. GitHub can see your IP address while it checks.") { UpdateRows() }
        }

        item {
            Section(7, seen, "About", "Sheer is the Firefox engine in a new coat.") {
                ValueRow("Version", version, true)
                ValueRow("Engine", "Gecko", false)
                LinkRow(Ic.Alert, "Recent stops", false) { onNotice("Recent stops", CrashLog.recent(ctx)) }
                LinkRow(Ic.Info, "Compiled code", false) { onNotice("Compiled code", FrameMeter.compiledReport(ctx)) }
                LinkRow(Ic.Clock, "Smoothness", false) { onNotice("Smoothness", FrameMeter.report()) }
                LinkRow(Ic.Reload, "Reset smoothness counter", false) {
                    FrameMeter.reset()
                    onNotice("Counter reset", "Go to a page, scroll it for about ten seconds, then open Smoothness.")
                }
                LinkRow(
                    Ic.Info, "What Sheer collects", false,
                ) {
                    onNotice(
                        "Nothing",
                        "Sheer has no account, no analytics, no ads and no server of its own. The sites you open see your connection, " +
                            "and the Firefox engine inside also talks to Mozilla for some services. The full list is still being checked.",
                    )
                }
            }
        }
    }
}
