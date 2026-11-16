package com.killswitch.sheer.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.killswitch.sheer.Browser
import com.killswitch.sheer.Settings
import com.killswitch.sheer.glass.LiquidBottomTab
import com.killswitch.sheer.glass.LiquidBottomTabs
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class TabInfo(val icon: Ic, val label: String)

private val TABS = listOf(TabInfo(Ic.World, "Browse"), TabInfo(Ic.Tabs, "Tabs"), TabInfo(Ic.Clock, "History"), TabInfo(Ic.Setup, "Settings"))

/**
 * The shell, built on the reference's: the scene the glass refracts, every tab built once and kept (a hidden tab is frozen and swallows
 * touches), the old page sliding away while the new one grows in, and the glass tab bar. [jump] counts up when a link from another app
 * should bring the Browse tab to the front.
 */
@Composable
fun SheerShell(browser: Browser, jump: Int) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity

    // Theme: follow the phone when asked to, and keep the bar icons readable on whichever palette is showing.
    val sysDark = isSystemInDarkTheme()
    LaunchedEffect(sysDark) { Aura.apply(Settings.theme(ctx), sysDark) }
    val dark = Aura.isDark
    LaunchedEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        activity.window.decorView.setBackgroundColor(Aura.Ink.toArgb())
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val backdrop = rememberLayerBackdrop()
    val scene = rememberLayerBackdrop()
    // One "already animated" set per tab, wiped each time you arrive, so a tab greets you with its stagger every visit.
    val seenFor = remember { Array(TABS.size) { mutableSetOf<Any>() } }

    var shown by remember { mutableIntStateOf(tab) }
    var leaving by remember { mutableIntStateOf(-1) }
    var dir by remember { mutableIntStateOf(1) }
    var steps by remember { mutableIntStateOf(1) }
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var warm by remember { mutableIntStateOf(-1) }
    // Tabs are built once and kept. Switching only moves what exists.
    val mounted = remember { mutableStateListOf(tab) }
    val arrivals = remember { mutableStateListOf<Int>().apply { repeat(TABS.size) { add(0) } } }

    fun switchTo(i: Int) {
        if (i == shown) { tab = i; return }
        steps = kotlin.math.abs(i - shown).coerceAtLeast(1)
        dir = if (i > shown) 1 else -1
        leaving = shown
        seenFor[i].clear()
        if (i !in mounted) mounted.add(i)
        arrivals[i] = arrivals[i] + 1
        shown = i
        tab = i
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            slide.snapTo(0f)
            slide.animateTo(1f, tween(260 + 40 * steps, easing = FastOutSlowInEasing))
            leaving = -1
        }
    }
    LaunchedEffect(jump) { if (jump > 0 && shown != 0) switchTo(0) }

    // Build the other tabs one at a time while the page sits untouched, and draw each faintly for a moment so the GPU does its first-time
    // work (effects, shaders) now. After this a tab switch has nothing left to build.
    LaunchedEffect(Unit) {
        delay(1200)
        for (i in TABS.indices) {
            if (i !in mounted) mounted.add(i)
            warm = i
            delay(500)
        }
        warm = -1
    }
    val reach = .10f + .04f * (steps - 1)

    val bars = WindowInsets.systemBars.asPaddingValues()
    // With the bars at the top the lists start below the tab bar (8 + 64 + 12 dp) and need no room at the bottom.
    val barsTop = UiPrefs.addressTop
    val pad = if (barsTop) PaddingValues(16.dp, bars.calculateTopPadding() + 84.dp, 16.dp, bars.calculateBottomPadding() + 24.dp)
    else PaddingValues(16.dp, bars.calculateTopPadding() + 12.dp, 16.dp, bars.calculateBottomPadding() + 110.dp)

    val page: @Composable (Int, Boolean) -> Unit = { i, active ->
        val seen = seenFor[i]
        when (i) {
            0 -> BrowseTab(browser, active)
            1 -> TabsTab(browser, pad, seen, onOpen = { switchTo(0) })
            2 -> HistoryTab(browser, pad, seen, onOpen = { switchTo(0) })
            else -> SettingsTab(browser, pad, seen, onNotice = { t, m -> dialog = t to m })
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Everything inside this box is what the glass refracts.
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            // The scene on its own, so controls inside a page refract the real background.
            Box(Modifier.layerBackdrop(scene).fillMaxSize()) { Canvas(Modifier.fillMaxSize()) { drawAuraScene() } }
            CompositionLocalProvider(LocalScene provides scene) {
                for (i in TABS.indices) if (i in mounted) key(i) {
                    val isShown = i == shown
                    val isLeaving = i == leaving
                    val live = isShown || isLeaving
                    Box(
                        Modifier.fillMaxSize().zIndex(if (isShown) 2f else if (isLeaving) 1f else 0f)
                            .graphicsLayer {
                                when {
                                    isShown -> {
                                        alpha = slide.value; translationX = dir * (1f - slide.value) * size.width * reach
                                        scaleX = .95f + .05f * slide.value; scaleY = scaleX
                                    }
                                    isLeaving -> {
                                        alpha = 1f - slide.value; translationX = -dir * slide.value * size.width * reach
                                        scaleX = 1f - .05f * slide.value; scaleY = scaleX
                                    }
                                    else -> alpha = if (i == warm) .01f else 0f // 1% so the GPU really draws it once, 0 the rest of the time
                                }
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .then(
                                if (live) Modifier
                                else Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
                                    // A hidden tab swallows any touch that falls through to it, and TalkBack skips it.
                                    awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
                                },
                            ),
                    ) { CompositionLocalProvider(LocalArrival provides arrivals[i]) { page(i, live) } }
                }
            }
        }

        LiquidBottomTabs(
            selectedTabIndex = { tab }, onTabSelected = { switchTo(it) }, backdrop = backdrop, tabsCount = TABS.size,
            modifier = if (barsTop) Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp)
            else Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TABS.forEachIndexed { i, t ->
                LiquidBottomTab({ switchTo(i) }) {
                    val on by selectAnim(tab == i)
                    val hop by animateFloatAsState(if (tab == i) 1f else 0f, spring(dampingRatio = .45f, stiffness = 380f), label = "tabHop")
                    // Dim grey on glass was hard to read over a bright page. Frosted uses near-full strength text for every tab.
                    val rest = Aura.Text.copy(alpha = if (UiPrefs.frosted) .78f else .9f)
                    Icon(t.icon, lerp(rest, Aura.Text, on), Modifier.size(26.dp).graphicsLayer {
                        scaleX = 1f + .14f * hop; scaleY = scaleX; translationY = -3.dp.toPx() * hop
                    })
                    BasicText(t.label, style = tsg(12f, lerp(rest, Aura.Text, on), FontWeight.ExtraBold))
                }
            }
        }

        dialog?.let { (t, m) -> NoticeDialog(backdrop, t, m) { dialog = null } }
    }
}
