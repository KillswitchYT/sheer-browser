package com.killswitch.sheer

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * The one Gecko engine for the whole process. The privacy settings are set here before any page loads, and the few the person can
 * change in Settings are re-applied live. The strict level also blocks fingerprinting and cryptominers.
 */
object Gecko {
    private var rt: GeckoRuntime? = null

    fun runtime(c: Context): GeckoRuntime {
        rt?.let { return it }
        val app = c.applicationContext
        val strict = Settings.strictTracking(app)
        val cb = ContentBlocking.Settings.Builder()
            .antiTracking(if (strict) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT)
            .enhancedTrackingProtectionLevel(if (strict) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
            .strictSocialTrackingProtection(strict)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookiePurging(true)
            .queryParameterStrippingEnabled(true)
            .safeBrowsing(if (Settings.safeBrowsing(app)) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE)
            .build()
        val settings = GeckoRuntimeSettings.Builder()
            .contentBlocking(cb)
            .allowInsecureConnections(httpsLevel(app))
            .globalPrivacyControlEnabled(true)
            .remoteDebuggingEnabled(false)
            .aboutConfigEnabled(false)
            .consoleOutput(false)
            .preferredColorScheme(colorScheme(app))
            // Lets addons.mozilla.org offer "Add to Firefox" like normal Firefox (Sheer shows its own question first), and keeps extensions
            // in a process of their own so a heavy one cannot stall the app.
            .extensionsWebAPIEnabled(true)
            .extensionsProcessEnabled(true)
            .build()
        return GeckoRuntime.create(app, settings).also { rt = it }
    }

    private fun httpsLevel(c: Context) = if (Settings.httpsOnly(c)) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL

    private fun colorScheme(c: Context) = when (Settings.theme(c)) {
        1 -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
        2 -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
    }

    fun applyTracking(c: Context) {
        val strict = Settings.strictTracking(c)
        val cb = runtime(c).settings.contentBlocking
        cb.setAntiTracking(if (strict) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT)
        cb.setEnhancedTrackingProtectionLevel(if (strict) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
        cb.setStrictSocialTrackingProtection(strict)
    }

    fun applySafeBrowsing(c: Context) {
        runtime(c).settings.contentBlocking.setSafeBrowsing(
            if (Settings.safeBrowsing(c)) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE,
        )
    }

    fun applyHttps(c: Context) { runtime(c).settings.setAllowInsecureConnections(httpsLevel(c)) }

    fun applyColorScheme(c: Context) { runtime(c).settings.setPreferredColorScheme(colorScheme(c)) }
}
