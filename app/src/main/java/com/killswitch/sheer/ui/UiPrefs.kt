package com.killswitch.sheer.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Choices that change how the screen is laid out, held as Compose state so a change in Settings shows at once. */
object UiPrefs {
    /** The address bar sits at the top instead of the bottom. */
    var addressTop by mutableStateOf(false)

    /** The page runs under the bottom glass bars, so the bars blur it. Off keeps the page clear of them. */
    var pageUnder by mutableStateOf(true)

    /** Frosted glass: the bars blur what is behind them and sit on a stronger tint, so their text and icons stay readable over any page.
     *  Off is the clear glass: no blur, almost no tint, the page bends at the rim. */
    var frosted by mutableStateOf(false)

    /** Calm glass: while a page is moving under the bars, the glass skips bending it and brings the bend back when the page rests. */
    var calmGlass by mutableStateOf(false)

    /** Skip painting the glass background where the page covers the whole screen. */
    var skipHidden by mutableStateOf(true)

    /** Set by the Browse tab: a real page is filling the screen, so the background behind it cannot be seen. */
    var browseCovers by mutableStateOf(false)

    /** Test: hide the bars while a page scrolls. */
    var testHide by mutableStateOf(false)

    /** Test: the page on a SurfaceView, which the engine draws to faster but the glass cannot see. */
    var fastPage by mutableStateOf(false)

    /** Test: ask Android to run Sheer at 60 Hz. */
    var cap60 by mutableStateOf(false)

    /** True while the page is being scrolled and for a moment after. Only ever read while drawing, so it recomposes nothing. */
    var scrolling by mutableStateOf(false)
}

/** Turns the engine's scroll reports into [UiPrefs.scrolling]: on at the first report, off a quarter second after the last one. */
object ScrollWatch {
    private val main = Handler(Looper.getMainLooper())
    private val rest = Runnable { UiPrefs.scrolling = false }

    fun moved() {
        if (!UiPrefs.scrolling) UiPrefs.scrolling = true
        main.removeCallbacks(rest)
        main.postDelayed(rest, 250)
    }
}

/** Asks Android to run this window at 60 Hz (or lets it choose again). Some phones ignore the request, the Smoothness report shows what ran. */
fun applyRefreshCap(a: android.app.Activity, on: Boolean) {
    val lp = a.window.attributes
    lp.preferredRefreshRate = if (on) 60f else 0f
    a.window.attributes = lp
}
