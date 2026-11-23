package com.killswitch.sheer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.toArgb
import com.killswitch.sheer.ui.Aura
import com.killswitch.sheer.ui.SheerShell
import com.killswitch.sheer.ui.UiPrefs
import com.killswitch.sheer.ui.applyRefreshCap

class MainActivity : ComponentActivity() {
    private val jump = mutableIntStateOf(0)
    private val browser: Browser get() = (application as SheerApp).browser

    // A page's file field: the system picker, then the answer goes back to the page.
    private var pickCb: ((List<Uri>) -> Unit)? = null
    private val pickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = pickCb
        pickCb = null
        val out = ArrayList<Uri>()
        val data = res.data
        if (res.resultCode == RESULT_OK && data != null) {
            data.data?.let { out.add(it) }
            data.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { out.add(it) } }
        }
        cb?.invoke(out.distinct())
    }

    // Camera, microphone and location are asked from Android only when a site actually wants them.
    private var permCb: ((Boolean) -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val cb = permCb
        permCb = null
        cb?.invoke(grants.isNotEmpty() && grants.values.all { it })
    }

    private fun hookPageRequests() {
        browser.setImmersive = { on ->
            val c = WindowCompat.getInsetsController(window, window.decorView)
            if (on) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                c.show(WindowInsetsCompat.Type.systemBars())
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        browser.filePicker = { types, multiple, cb ->
            val mimes = types.filter { it.isNotBlank() }
            val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimes.size == 1) mimes[0] else "*/*"
                if (mimes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            }
            pickCb = cb
            try { pickLauncher.launch(i) } catch (e: Exception) { pickCb = null; cb(emptyList()) }
        }
        browser.askAndroid = { perms, cb ->
            val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) cb(true) else { permCb = cb; permLauncher.launch(missing.toTypedArray()) }
        }
    }

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
        UiPrefs.frosted = Settings.frosted(this)
        UiPrefs.calmGlass = Settings.calmGlass(this)
        UiPrefs.skipHidden = Settings.skipHidden(this)
        UiPrefs.testHide = Settings.testHide(this)
        UiPrefs.cap60 = Settings.cap60(this)
        applyRefreshCap(this, UiPrefs.cap60)
        // The theme is one shared object, so every Activity applies the saved choice itself before drawing anything.
        Aura.apply(Settings.theme(this), (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        val bars = if (Aura.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        window.decorView.setBackgroundColor(Aura.Ink.toArgb())
        Gecko.runtime(this)
        hookPageRequests()
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

    override fun onStart() {
        super.onStart()
        FrameMeter.attach(this)
    }

    override fun onStop() {
        super.onStop()
        FrameMeter.detach()
        browser.save()
    }
}
