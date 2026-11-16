package com.killswitch.sheer.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.killswitch.sheer.BTab
import com.killswitch.sheer.Browser
import com.killswitch.sheer.UrlInput
import com.killswitch.sheer.glass.LiquidButton
import com.killswitch.sheer.glass.LiquidField
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.mozilla.geckoview.GeckoView

/**
 * The page, and over it one glass address bar with back, forward and reload. The page is drawn through a TextureView (not the faster
 * SurfaceView) so that it lives inside the same layers as everything else and the glass can see it. Whether the glass really blurs the
 * live page is the first thing to check on the phone, see TESTING.md.
 */
@Composable
fun BrowseTab(browser: Browser, active: Boolean) {
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    val tab = browser.current
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    // What the glass bars refract: the scene and the page together. The bars are drawn OUTSIDE this layer, never inside it.
    val backdrop = rememberLayerBackdrop()

    // The tab bar is always at the bottom (88 dp with its padding); the address row (48 dp plus a gap each side) is at the bottom or the
    // top, as chosen in Settings. The page never starts under the top bars. At the bottom it either runs under the glass (the default look,
    // the glass blurs the page, the end of a page can hide behind the bars) or stops above the bars (Settings, "Page scrolls under the bars").
    val top = UiPrefs.addressTop
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageTop = if (top) statusTop + 64.dp else statusTop
    val under = UiPrefs.pageUnder
    val pageBottom = if (under) 0.dp else if (top) navBottom + 88.dp else navBottom + 152.dp

    fun leaveEdit() { editing = false; focusManager.clearFocus() }
    fun startEdit() {
        val u = tab?.url.orEmpty()
        field = TextFieldValue(u, TextRange(0, u.length))
        editing = true
    }
    fun submit() { browser.go(field.text); leaveEdit() }

    val activity = LocalContext.current as? Activity
    val secure = active && tab?.incognito == true
    DisposableEffect(secure) {
        if (secure) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LaunchedEffect(browser.selectedId) { editing = false }
    LaunchedEffect(tab?.id, tab?.session, tab?.crashed) {
        if (tab != null && tab.session == null && tab.url.isNotEmpty() && !tab.crashed) browser.ensure(tab)
    }
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }
    LaunchedEffect(active) { if (!active) leaveEdit() }
    BackHandler(enabled = active && editing) { leaveEdit() }
    BackHandler(enabled = active && !editing && tab?.canBack == true) { browser.back() }

    val loading = tab?.loading == true
    val progress by animateFloatAsState(
        if (loading) (tab?.progress ?: 0) / 100f else 0f, tween(180), label = "progress",
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Canvas(Modifier.fillMaxSize()) { drawAuraScene() }
            if (tab != null) {
                when {
                    tab.crashed -> Box(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
                        EmptyState(Ic.Alert, "This page crashed", "The engine stopped on this tab. Your other tabs are fine.", "Reload") { browser.recover(tab) }
                    }
                    tab.session == null -> StartPage(tab.incognito, ::startEdit)
                    else -> GeckoPage(browser, tab, Modifier.fillMaxSize().padding(top = pageTop, bottom = pageBottom))
                }
            }
        }

        if (progress > .001f) {
            Box(Modifier.align(Alignment.TopStart).statusBarsPadding().fillMaxWidth(progress).height(2.dp).background(Aura.Accent))
        }

        Row(
            (if (top) Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp)
            else Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = if (editing) 8.dp else 96.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!editing) NavButton(Ic.Back, backdrop, enabled = tab?.canBack == true) { browser.back() }

            LiquidField(backdrop, Modifier.weight(1f).height(48.dp)) {
                if (editing) {
                    Icon(Ic.Search, Aura.Muted, Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    BasicTextField(
                        field, { field = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true, textStyle = tsg(15f),
                        cursorBrush = SolidColor(Aura.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        decorationBox = { inner ->
                            if (field.text.isEmpty()) BasicText("Search or type an address", style = tsg(15f, Aura.Muted))
                            inner()
                        },
                    )
                    Box(Modifier.pressable { leaveEdit() }.padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) {
                        BasicText("Cancel", style = tsg(14f, Aura.AccentText, FontWeight.Bold))
                    }
                } else {
                    val host = UrlInput.host(tab?.url.orEmpty())
                    Row(Modifier.weight(1f).fillMaxSize().pressable { startEdit() }, verticalAlignment = Alignment.CenterVertically) {
                        if (tab?.incognito == true) {
                            Icon(Ic.Private, Aura.AccentText, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                        }
                        if (host.isNotEmpty()) {
                            Icon(
                                if (tab?.secure == true) Ic.Lock else Ic.Alert,
                                if (tab?.secure == true) Aura.Muted else Aura.Rose, Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                        } else {
                            Icon(Ic.Search, Aura.Muted, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                        }
                        BasicText(
                            host.ifEmpty { "Search or type an address" },
                            style = tsg(15f, if (host.isEmpty()) Aura.Muted else Aura.Text, FontWeight.Bold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (tab?.session != null) {
                        Box(Modifier.pressable { if (loading) browser.stop() else browser.reload() }.padding(start = 10.dp)) {
                            Icon(if (loading) Ic.Close else Ic.Reload, Aura.Text, Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (!editing) NavButton(Ic.Chevron, backdrop, enabled = tab?.canForward == true) { browser.forward() }
        }
    }
}

/** A round glass button, dimmed and dead when there is nowhere to go. */
@Composable
private fun NavButton(icon: Ic, backdrop: Backdrop, enabled: Boolean, onClick: () -> Unit) {
    LiquidButton(
        if (enabled) onClick else ({}), backdrop,
        Modifier.size(44.dp).graphicsLayer { alpha = if (enabled) 1f else .4f },
        isInteractive = enabled, height = 44.dp, horizontalPadding = 0.dp,
    ) { Icon(icon, Aura.Text, Modifier.size(20.dp)) }
}

/** What a tab with no page shows: what the app is for, and the one thing to do next. */
@Composable
private fun StartPage(incognito: Boolean, onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 120.dp).staggerIn(0),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (incognito) Ic.Private else Ic.Shield, Aura.Muted, Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        BasicText(if (incognito) "Private tab" else "Sheer", style = ts(34f, weight = FontWeight.ExtraBold))
        Spacer(Modifier.height(6.dp))
        BasicText(
            if (incognito) "No history, no cookies and no saved tabs from this tab. Close all private tabs in Tabs to wipe it."
            else "Sheer keeps nothing about you: no account, no analytics, no ads.",
            style = ts(15f, Aura.Muted),
        )
        Spacer(Modifier.height(18.dp))
        AccentPill("Search or type an address", onSearch)
    }
}

/** The real page. Gecko draws into a view that Compose hosts; a new session is handed to it once the engine has opened it. */
@Composable
private fun GeckoPage(browser: Browser, tab: BTab, modifier: Modifier) {
    val session = tab.session ?: return
    val tick = tab.url.length + tab.progress // read here so this runs again after a freshly opened session becomes ready
    val ink = Aura.Ink.toArgb()
    AndroidView(
        factory = { c ->
            GeckoView(c).also {
                it.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                it.coverUntilFirstPaint(ink)
                browser.view = it
            }
        },
        modifier = modifier,
        update = { v ->
            v.tag = tick
            if (session.isOpen && v.session !== session) {
                if (v.session != null) v.releaseSession() // let go of the old page first, as the engine asks
                v.setSession(session)
            }
        },
        onRelease = { v ->
            if (browser.view === v) browser.view = null
            v.releaseSession()
        },
    )
}
