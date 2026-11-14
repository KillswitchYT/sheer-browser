package com.killswitch.sheer.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
import com.killswitch.sheer.Gecko
import com.killswitch.sheer.Settings
import com.killswitch.sheer.UrlInput
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

@Composable
fun SettingsTab(pad: PaddingValues, seen: MutableSet<Any>, onNotice: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val sysDark = isSystemInDarkTheme()
    var theme by remember { mutableIntStateOf(Settings.theme(ctx)) }
    var engine by remember { mutableIntStateOf(Settings.engine(ctx)) }
    var strict by remember { mutableStateOf(Settings.strictTracking(ctx)) }
    var https by remember { mutableStateOf(Settings.httpsOnly(ctx)) }
    var safe by remember { mutableStateOf(Settings.safeBrowsing(ctx)) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Column(Modifier.staggerIn(0, seen.add("set-head"))) { PageTitle("Settings", "Kept on this phone only") } }

        item {
            Section(1, seen, "Appearance", "Light, dark, or whatever the phone does.") {
                THEMES.forEachIndexed { i, name ->
                    Choice(name, null, theme == i, i == 0) {
                        theme = i
                        Settings.setInt(ctx, "th", i)
                        Aura.apply(i, sysDark)
                        Gecko.applyColorScheme(ctx)
                    }
                }
            }
        }

        item {
            Section(2, seen, "Search", "What the address bar uses for anything that is not a web address.") {
                UrlInput.ENGINES.forEachIndexed { i, e ->
                    Choice(e.name, null, engine == i, i == 0) { engine = i; Settings.setInt(ctx, "se", i) }
                }
            }
        }

        item {
            Section(3, seen, "Privacy", "Applied to every page, right away.") {
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
            Section(4, seen, "About", "Sheer is the Firefox engine in a new coat.") {
                ValueRow("Version", version, true)
                ValueRow("Engine", "Gecko", false)
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
