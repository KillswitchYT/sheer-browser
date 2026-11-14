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

class MainActivity : ComponentActivity() {
    private val jump = mutableIntStateOf(0)
    private val browser: Browser get() = (application as SheerApp).browser

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
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
