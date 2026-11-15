package com.killswitch.sheer

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.toArgb
import com.killswitch.sheer.ui.Aura
import com.killswitch.sheer.ui.SheerShell
import com.killswitch.sheer.ui.UiPrefs

class MainActivity : ComponentActivity() {
    private val jump = mutableIntStateOf(0)
    private val browser: Browser get() = (application as SheerApp).browser

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // If the app died last time, say why before starting anything, so the report can be read even if it would die again.
        val report = CrashLog.pending(this)
        if (report != null) showReport(report) { start(b) } else start(b)
    }

    private fun showReport(report: String, then: () -> Unit) {
        val d = android.app.AlertDialog.Builder(this)
            .setTitle("Sheer stopped last time")
            .setMessage(report)
            .setNeutralButton("Copy", null)
            .setPositiveButton("Continue") { _, _ -> then() }
            .setCancelable(false)
            .create()
        d.show()
        // Copy must not close the dialog, so it is wired after show().
        d.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Sheer crash report", report))
        }
    }

    private fun start(b: Bundle?) {
        UiPrefs.addressTop = Settings.addressTop(this)
        UiPrefs.pageUnder = Settings.pageUnder(this)
        // The theme is one shared object, so every Activity applies the saved choice itself before drawing anything.
        Aura.apply(Settings.theme(this), (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        val bars = if (Aura.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        window.decorView.setBackgroundColor(Aura.Ink.toArgb())
        Gecko.runtime(this)
        if (b == null) open(intent) // a re-created activity still holds the old intent, do not open the link twice
        setContent { SheerShell(browser, jump.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    private fun open(i: Intent?) {
        if (i?.action != Intent.ACTION_VIEW) return
        val url = i.dataString ?: return
        if (!UrlInput.isWeb(url)) return
        browser.openIncoming(url)
        jump.intValue++ // bring the Browse tab to the front
    }

    override fun onStop() {
        super.onStop()
        browser.save()
    }
}
