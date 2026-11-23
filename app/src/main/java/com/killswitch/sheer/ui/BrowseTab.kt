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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.killswitch.sheer.BTab
import com.killswitch.sheer.Browser
import com.killswitch.sheer.PageActions
import com.killswitch.sheer.UrlInput
import com.killswitch.sheer.glass.LiquidButton
import com.killswitch.sheer.glass.LiquidField
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
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
    // Find in page: the bar takes the place of the address bar while it is open.
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<GeckoSession.FinderResult?>(null) }
    val findFocus = remember { FocusRequester() }
    // What the glass bars refract: the scene and the page together. The bars are drawn OUTSIDE this layer, never inside it.
    val live = rememberLayerBackdrop()
    // The bars refract this one. While the page scrolls and Calm glass is on, it draws nothing, so the bars do not replay the page every frame.
    val backdrop = rememberBackdrop(live) { drawBackdrop -> if (!(UiPrefs.calmGlass && UiPrefs.scrolling)) drawBackdrop() }

    // The tab bar is always at the bottom (88 dp with its padding); the address row (48 dp plus a gap each side) is at the bottom or the
    // top, as chosen in Settings. The page never starts under the top bars. At the bottom it either runs under the glass (the default look,
    // the glass blurs the page, the end of a page can hide behind the bars) or stops above the bars (Settings, "Page scrolls under the bars").
    val top = UiPrefs.addressTop
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Bars at the top: the tab bar sits under the status bar (8 dp gap, 64 dp tall), the address row under it (8 dp gap, 48 dp tall).
    val under = UiPrefs.pageUnder
    // Page under the bars: the page fills the whole screen and the bars float over it, so no strip of background shows above or below it.
    // Off: the page stops where the bars end, and the bars sit on the dark scene.
    val pageTop = if (under) 0.dp else if (top) statusTop + 136.dp else statusTop
    // Where text screens (start page, crash page) may sit so the bars never cover them.
    val contentTop = if (top) statusTop + 136.dp else statusTop
    val contentBottom = if (top) navBottom + 24.dp else 120.dp
    val pageBottom = if (under) 0.dp else if (top) navBottom else navBottom + 152.dp

    fun leaveEdit() { editing = false; focusManager.clearFocus() }
    fun startEdit() {
        val u = tab?.url.orEmpty()
        field = TextFieldValue(u, TextRange(0, u.length))
        editing = true
    }
    fun submit() { browser.go(field.text); leaveEdit() }
    fun closeFind() { finding = false; focusManager.clearFocus() }
    /** Text null means "the same words again": that is how next and previous work. */
    fun doFind(text: String?, flags: Int) {
        val s = tab?.session ?: return
        s.finder.find(text, flags).then(
            object : GeckoResult.OnValueListener<GeckoSession.FinderResult, Void> {
                override fun onValue(value: GeckoSession.FinderResult?): GeckoResult<Void>? {
                    found = value
                    return null
                }
            },
        )
    }

    val activity = LocalContext.current as? Activity
    val secure = active && tab?.incognito == true
    DisposableEffect(secure) {
        if (secure) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LaunchedEffect(browser.selectedId) { editing = false; finding = false }
    LaunchedEffect(tab?.id, tab?.session, tab?.crashed) {
        if (tab != null && tab.session == null && tab.url.isNotEmpty() && !tab.crashed) browser.ensure(tab)
    }
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }
    LaunchedEffect(active) { if (!active) { leaveEdit(); finding = false } }
    LaunchedEffect(finding) {
        if (finding) {
            delay(80)
            findFocus.requestFocus()
        } else {
            query = ""
            found = null
            tab?.session?.finder?.clear()
        }
    }
    LaunchedEffect(query, finding) {
        if (finding) {
            if (query.isEmpty()) {
                found = null
                tab?.session?.finder?.clear()
            } else {
                delay(200)
                doFind(query, GeckoSession.FINDER_FIND_FORWARD)
            }
        }
    }
    BackHandler(enabled = active && editing) { leaveEdit() }
    BackHandler(enabled = active && finding) { closeFind() }
    BackHandler(enabled = active && !editing && tab?.canBack == true) { browser.back() }

    val loading = tab?.loading == true
    val covers = UiPrefs.skipHidden && under && tab != null && !tab.crashed && tab.session != null
    SideEffect { UiPrefs.browseCovers = covers }
    // Kept as a State and only read while drawing: reading it here would recompose the whole screen on every frame of a page load.
    val progress = animateFloatAsState(
        if (loading) (tab?.progress ?: 0) / 100f else 0f, tween(180), label = "progress",
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(live)) {
            // A page that fills the screen hides the background completely, so painting it again for every page frame is wasted work.
            Canvas(Modifier.fillMaxSize()) { if (!covers) drawAuraScene() }
            if (tab != null) {
                when {
                    tab.crashed -> Box(Modifier.fillMaxSize().padding(top = contentTop).padding(horizontal = 16.dp)) {
                        EmptyState(Ic.Alert, "This page crashed", "The engine stopped on this tab. Your other tabs are fine.", "Reload") { browser.recover(tab) }
                    }
                    tab.session == null -> StartPage(tab.incognito, contentTop, contentBottom, ::startEdit)
                    else -> GeckoPage(browser, tab, Modifier.fillMaxSize().padding(top = pageTop, bottom = pageBottom))
                }
            }
        }

        Box(
            Modifier.align(Alignment.TopStart).statusBarsPadding().fillMaxWidth().height(2.dp).drawBehind {
                val p = progress.value
                if (p > .001f) drawRect(Aura.Accent, size = Size(size.width * p, size.height))
            },
        )

        Row(
            (if (top) Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 80.dp)
            else Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = if (editing || finding) 8.dp else 96.dp))
                .graphicsLayer {
                    // Test switch: the bars vanish while the page scrolls, to see whether the glass costs anything.
                    alpha = if (UiPrefs.testHide && UiPrefs.scrolling) 0f else 1f
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!editing && !finding) NavButton(Ic.Back, backdrop, enabled = tab?.canBack == true) { browser.back() }

            LiquidField(backdrop, Modifier.weight(1f).height(48.dp)) {
                if (finding) {
                    Icon(Ic.Search, Aura.Muted, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    BasicTextField(
                        query, { query = it }, Modifier.weight(1f).focusRequester(findFocus), singleLine = true, textStyle = tsg(15f),
                        cursorBrush = SolidColor(Aura.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doFind(null, GeckoSession.FINDER_FIND_FORWARD) }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) BasicText("Find in page", style = tsg(15f, Aura.Muted))
                            inner()
                        },
                    )
                    val f = found
                    if (query.isNotEmpty() && f != null) {
                        BasicText(
                            if (f.total > 0) "${f.current}/${f.total}" else "None",
                            modifier = Modifier.padding(start = 8.dp), style = tsg(13f, Aura.Muted, FontWeight.Bold),
                        )
                    }
                    Box(Modifier.semantics { contentDescription = "Previous match" }.pressable { doFind(null, GeckoSession.FINDER_FIND_BACKWARDS) }.padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) {
                        Icon(Ic.Chevron, Aura.Text, Modifier.size(20.dp).graphicsLayer { rotationZ = -90f })
                    }
                    Box(Modifier.semantics { contentDescription = "Next match" }.pressable { doFind(null, GeckoSession.FINDER_FIND_FORWARD) }.padding(start = 6.dp, top = 8.dp, bottom = 8.dp)) {
                        Icon(Ic.Chevron, Aura.Text, Modifier.size(20.dp).graphicsLayer { rotationZ = 90f })
                    }
                    Box(Modifier.pressable { closeFind() }.padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) {
                        BasicText("Done", style = tsg(14f, Aura.AccentText, FontWeight.Bold))
                    }
                } else if (editing) {
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
                        Box(Modifier.semantics { contentDescription = "Page menu" }.pressable { tab?.let { PageActions.pageMenu(browser, it) { finding = true } } }.padding(start = 12.dp)) {
                            Icon(Ic.More, Aura.Text, Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (!editing && !finding) NavButton(Ic.Chevron, backdrop, enabled = tab?.canForward == true) { browser.forward() }
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
private fun StartPage(incognito: Boolean, topInset: Dp, bottomInset: Dp, onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset).padding(horizontal = 20.dp).staggerIn(0),
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
