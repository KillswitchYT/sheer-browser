package com.killswitch.sheer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.killswitch.sheer.Browser
import com.killswitch.sheer.Extensions
import com.killswitch.sheer.glass.LiquidPanel
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/**
 * The quick menu: hold the address bar and it rises from the bottom, in glass, with a little bounce. The same things as Firefox's menu:
 * back, forward, share, refresh; bookmark, find, computer version of the site; the extensions (each one opens its own panel); and
 * shortcuts to history, bookmarks, downloads and settings. [openTab] switches the app to one of its screens.
 */
@Composable
fun QuickMenu(browser: Browser, backdrop: Backdrop, openTab: (Int) -> Unit) {
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var extOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = .78f, stiffness = Spring.StiffnessMediumLow)) }

    /** Slides the menu away, then does [after]. Doing it in this order keeps [after] from being cut off when the menu leaves the screen. */
    fun close(after: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch {
            enter.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            after()
            UiPrefs.quickMenu = false
        }
    }
    BackHandler { close() }

    val tab = browser.current
    val session = tab?.session
    var marked by remember { mutableStateOf(false) }
    LaunchedEffect(tab?.url) { tab?.url?.takeIf { it.isNotEmpty() }?.let { u -> browser.isBookmarked(u) { m -> marked = m } } }
    val loading = tab?.loading == true
    val hasPage = tab != null && session != null && tab.url.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = .45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        )
        LiquidPanel(
            backdrop,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp).navigationBarsPadding().padding(bottom = 10.dp)
                .graphicsLayer {
                    translationY = (1f - enter.value) * 560.dp.toPx()
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            corner = 34.dp,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp),
        ) {
            // Pull down on the handle to put it away.
            Box(
                Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 12f) close() } }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = 44.dp, height = 5.dp).background(Aura.overlay(.35f), RoundedCornerShape(50))) }

            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth()) {
                    ActionCell(Ic.Back, "Back", tab?.canBack == true) { close { browser.back() } }
                    ActionCell(Ic.Chevron, "Forward", tab?.canForward == true) { close { browser.forward() } }
                    ActionCell(Ic.Share, "Share", hasPage) { close { tab?.url?.let { browser.share(it) } } }
                    ActionCell(if (loading) Ic.Close else Ic.Reload, if (loading) "Stop" else "Refresh", session != null) {
                        close { if (loading) browser.stop() else browser.reload() }
                    }
                }
                Spacer(Modifier.size(8.dp))
                MenuRow(Ic.Star, if (marked) "Remove bookmark" else "Bookmark page", hasPage) {
                    close { tab?.let { browser.toggleBookmark(it.url, it.title.ifEmpty { it.url }, marked) } }
                }
                MenuRow(Ic.Search, "Find in page", hasPage) { close { UiPrefs.findRequest++ } }
                MenuRow(Ic.Desktop, "Desktop site", hasPage, trailing = { Pill(if (tab?.desktop == true) "On" else "Off") }) { close { browser.toggleDesktop() } }

                // Extensions: the list opens in place, like Firefox's.
                val enabled = Extensions.list.filter { Extensions.isOn(it) }
                val arrow by animateFloatAsState(if (extOpen) 180f else 0f, tween(220), label = "extArrow")
                MenuRow(
                    Ic.Puzzle, "Extensions", true,
                    sub = if (enabled.isEmpty()) "None yet" else enabled.joinToString(", ") { Extensions.nameOf(it) },
                    trailing = {
                        if (enabled.isNotEmpty()) Pill(enabled.size.toString())
                        Spacer(Modifier.size(8.dp))
                        Icon(Ic.Chevron, Aura.Text, Modifier.size(20.dp).graphicsLayer { rotationZ = 90f + arrow })
                    },
                ) { extOpen = !extOpen }
                AnimatedVisibility(extOpen, enter = expandVertically(tween(240)) + fadeIn(tween(240)), exit = shrinkVertically(tween(200)) + fadeOut(tween(160))) {
                    Column {
                        enabled.forEach { ext ->
                            ExtensionRow(
                                ext, session,
                                onOpen = { close { Extensions.press(ext.id, session) } },
                                onGear = { close { Extensions.optionsOf(ext)?.let { browser.newTab(it, false) } } },
                            )
                        }
                        MenuRow(Ic.Puzzle, "Manage extensions", true, plain = true) { close { UiPrefs.library = 2 } }
                    }
                }

                val moreArrow by animateFloatAsState(if (moreOpen) 180f else 0f, tween(220), label = "moreArrow")
                MenuRow(
                    Ic.More, "More", true,
                    trailing = { Icon(Ic.Chevron, Aura.Text, Modifier.size(20.dp).graphicsLayer { rotationZ = 90f + moreArrow }) },
                ) { moreOpen = !moreOpen }
                AnimatedVisibility(moreOpen, enter = expandVertically(tween(240)) + fadeIn(tween(240)), exit = shrinkVertically(tween(200)) + fadeOut(tween(160))) {
                    Column {
                        MenuRow(Ic.Share, "Copy link", hasPage, plain = true) { close { tab?.url?.let { browser.copy(it, "Link") } } }
                    }
                }

                Spacer(Modifier.size(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tile(Ic.Clock, "History") { close { UiPrefs.library = 0 } }
                    Tile(Ic.Star, "Bookmarks") { close { UiPrefs.library = 1 } }
                    Tile(Ic.Download, "Downloads") { close { openTab(2) } }
                }
                Spacer(Modifier.size(6.dp))
                MenuRow(Ic.Setup, "Settings", true) { close { openTab(3) } }
            }
        }
    }
}

@Composable
private fun RowScope.ActionCell(icon: Ic, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).graphicsLayer { alpha = if (enabled) 1f else .35f }.then(if (enabled) Modifier.pressable(onClick) else Modifier).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, Aura.Text, Modifier.size(24.dp))
        Spacer(Modifier.size(6.dp))
        BasicText(label, style = ts(13f, Aura.Text, FontWeight.Bold), maxLines = 1)
    }
}

/** One row of the menu. [plain] drops the tinted background (used for rows inside an open group). */
@Composable
private fun MenuRow(
    icon: Ic, text: String, enabled: Boolean, sub: String? = null, plain: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {}, onClick: () -> Unit,
) {
    Row(
        Modifier.padding(top = 6.dp).fillMaxWidth()
            .background(if (plain) Aura.overlay(.05f) else Aura.overlay(.08f), RoundedCornerShape(22.dp))
            .graphicsLayer { alpha = if (enabled) 1f else .4f }
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, Aura.Text, Modifier.size(24.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            BasicText(text, style = ts(17f, Aura.Text, FontWeight.Bold), maxLines = 1)
            if (sub != null) BasicText(sub, style = ts(14f, Aura.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun Pill(text: String) {
    Box(Modifier.background(Aura.overlay(.14f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        BasicText(text, style = ts(14f, Aura.Text, FontWeight.ExtraBold), maxLines = 1)
    }
}

@Composable
private fun RowScope.Tile(icon: Ic, label: String, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).background(Aura.overlay(.08f), RoundedCornerShape(22.dp)).pressable(onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, Aura.Text, Modifier.size(24.dp))
        Spacer(Modifier.size(8.dp))
        BasicText(label, style = ts(13f, Aura.Text, FontWeight.Bold), maxLines = 1)
    }
}

/** An extension in the menu: its icon and name, the little number it shows for this page (uBlock Origin: what it blocked), and its settings. */
@Composable
private fun ExtensionRow(ext: WebExtension, session: GeckoSession?, onOpen: () -> Unit, onGear: () -> Unit) {
    val badge = Extensions.badgeOf(ext.id, session)
    val icon = Extensions.icons[ext.id]
    val hasButton = Extensions.hasButton(ext.id)
    val options = Extensions.optionsOf(ext)
    Row(
        Modifier.padding(top = 6.dp).fillMaxWidth().background(Aura.overlay(.05f), RoundedCornerShape(22.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).then(if (hasButton) Modifier.pressable(onOpen) else Modifier).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Image(icon, null, Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))) else Icon(Ic.Puzzle, Aura.Text, Modifier.size(26.dp))
            Spacer(Modifier.size(16.dp))
            BasicText(Extensions.nameOf(ext), modifier = Modifier.weight(1f), style = ts(16f, Aura.Text, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (badge.isNotEmpty()) Pill(badge)
        }
        if (options != null) {
            Box(Modifier.width(1.dp).size(width = 1.dp, height = 30.dp).background(Aura.overlay(.14f)))
            Box(Modifier.pressable(onGear).padding(horizontal = 18.dp, vertical = 14.dp)) { Icon(Ic.Setup, Aura.Muted, Modifier.size(22.dp)) }
        }
    }
}
