package com.killswitch.sheer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.killswitch.sheer.Browser
import com.killswitch.sheer.Extensions
import kotlinx.coroutines.launch

/**
 * A full-screen page that rises over the app: 0 History, 1 Bookmarks, 2 Extensions. Back (or the arrow) slides it away.
 * They used to be tabs; the bottom bar now has Downloads where History was, and these open from the quick menu.
 */
@Composable
fun LibraryScreen(browser: Browser, which: Int, backdrop: com.kyant.backdrop.Backdrop) {
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    fun close(after: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch {
            enter.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
            after()
            UiPrefs.library = null
        }
    }
    BackHandler { close() }
    val seen = remember { mutableSetOf<Any>() }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 120.dp.toPx() }.background(Aura.Ink),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawAuraScene() }
        val pad = PaddingValues(start = 20.dp, end = 20.dp, top = 78.dp, bottom = 40.dp)
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            when (which) {
                2 -> ExtensionsPage(browser, pad, seen) { close() }
                else -> HistoryTab(browser, pad, seen, onOpen = { close() }, startOnMarks = which == 1)
            }
        }
        Row(Modifier.statusBarsPadding().padding(start = 10.dp, top = 10.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.pressable { close() }.padding(12.dp)) { Icon(Ic.Back, Aura.Text, Modifier.size(26.dp)) }
        }
    }
}

/** Every extension: switch it on or off, open its own settings, remove it; and the way to find more. */
@Composable
private fun ExtensionsPage(browser: Browser, pad: PaddingValues, seen: MutableSet<Any>, close: () -> Unit) {
    LaunchedEffect(Unit) { Extensions.refresh() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Extensions", "Add-ons that run inside the page engine. Each one asks before it is added.") }
        item {
            Section(0, seen, "Installed", "Switch one off to stop it without removing it.") {
                val exts = Extensions.list
                if (exts.isEmpty()) BasicText("Nothing installed yet.", style = ts(15f, Aura.Muted), modifier = Modifier.padding(vertical = 18.dp))
                exts.forEachIndexed { i, ext ->
                    val name = Extensions.nameOf(ext)
                    ToggleRow(
                        name,
                        (if (ext.isBuiltIn) "Comes with Sheer" else "Added by you") + (ext.metaData?.version?.let { ", version $it" } ?: ""),
                        Extensions.isOn(ext), i == 0,
                    ) { Extensions.setOn(ext, it) }
                    Extensions.optionsOf(ext)?.let { url -> LinkRow(Ic.Setup, "Settings of $name", false) { browser.newTab(url, false); close() } }
                    if (!ext.isBuiltIn) LinkRow(Ic.Trash, "Remove $name", false) { Extensions.remove(ext) }
                }
            }
        }
        item {
            Section(1, seen, "Add more", "Opens Mozilla's add-on site. Press Add to Firefox on an extension and Sheer asks you first.") {
                LinkRow(Ic.World, "Find extensions", true) { browser.newTab("https://addons.mozilla.org/en-US/android/", false); close() }
            }
        }
    }
}
