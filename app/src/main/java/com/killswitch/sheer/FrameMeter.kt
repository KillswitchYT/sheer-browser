package com.killswitch.sheer

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import android.view.FrameMetrics
import android.view.Window
import java.util.Locale

/**
 * Times every frame that Sheer's own window draws (the bars, the glass and the page texture), so the phone can say how smooth the app
 * really is. It only counts. Nothing is stored and nothing leaves the phone. It cannot see the engine's own work inside a page.
 */
object FrameMeter {
    private val lock = Any()
    private var frames = 0
    private var slow = 0      // took longer than one screen refresh
    private var veryslow = 0  // took longer than two
    private var worstMs = 0.0
    private var totalMs = 0.0
    private var budgetMs = 1000.0 / 60.0
    private var attached: Window? = null
    private var thread: HandlerThread? = null
    private var display: Display? = null

    private val listener = Window.OnFrameMetricsAvailableListener { _, m, _ ->
        // The very first frame builds the whole screen and always looks slow, so it is not counted.
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 1L) {
            val ms = m.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
            synchronized(lock) {
                if (frames % 30 == 0) display?.refreshRate?.let { if (it >= 30f) budgetMs = 1000.0 / it }
                frames++
                totalMs += ms
                if (ms > worstMs) worstMs = ms
                if (ms > budgetMs) slow++
                if (ms > budgetMs * 2) veryslow++
            }
        }
    }

    fun attach(a: Activity) {
        if (attached === a.window) return
        detach()
        val t = HandlerThread("sheer-frames").also { it.start() }
        thread = t
        @Suppress("DEPRECATION")
        val d = a.windowManager.defaultDisplay
        display = d
        val hz = d.refreshRate
        synchronized(lock) { budgetMs = 1000.0 / (if (hz >= 30f) hz else 60f) }
        a.window.addOnFrameMetricsAvailableListener(listener, Handler(t.looper))
        attached = a.window
    }

    fun detach() {
        attached?.let { runCatching { it.removeOnFrameMetricsAvailableListener(listener) } }
        attached = null
        thread?.quitSafely()
        thread = null
    }

    fun reset() = synchronized(lock) {
        frames = 0; slow = 0; veryslow = 0; worstMs = 0.0; totalMs = 0.0
        display?.refreshRate?.let { if (it >= 30f) budgetMs = 1000.0 / it }
    }

    fun report(): String = synchronized(lock) {
        if (frames == 0) {
            "Nothing counted yet. Tap Reset smoothness counter, scroll a page for about ten seconds, then open this again."
        } else {
            String.format(
                Locale.US,
                "%d frames counted at %.0f Hz.\n\nAverage: %.1f ms\nWorst: %.0f ms\nSlower than one screen refresh (%.1f ms): %d%%\nSlower than two refreshes: %d%%\n\n" +
                    "Lower is smoother. This counts Sheer's own drawing (bars, glass, page texture), not the work the engine does inside a page.",
                frames, 1000.0 / budgetMs, totalMs / frames, worstMs, budgetMs, slow * 100 / frames, veryslow * 100 / frames,
            )
        }
    }
}
