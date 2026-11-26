package com.killswitch.sheer

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.killswitch.sheer.ui.Ask
import com.killswitch.sheer.ui.PageAsks
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Browser extensions through the engine's own WebExtension support. uBlock Origin ships inside the app (assets/extensions/ublock) and is
 * switched on for every tab, private ones too. Others, Bitwarden for one, are added from Mozilla's add-on site only when the person asks.
 * A toolbar button opens its popup in a sheet over the app; extension pages open as normal tabs.
 */
object Extensions {
    const val UBLOCK_ID = "uBlock0@raymondhill.net"
    private const val UBLOCK_URI = "resource://android/assets/extensions/ublock/"
    const val BITWARDEN_URL = "https://addons.mozilla.org/firefox/downloads/latest/bitwarden-password-manager/latest.xpi"

    /** What the engine says is installed. Read again with [refresh]. */
    var list by mutableStateOf<List<WebExtension>>(emptyList())
        private set

    /** Goes up when an extension changes its toolbar button, so a menu showing them can read them again. */
    var version by mutableIntStateOf(0)
        private set

    /** The popup that is open over the app, if any. */
    var popup by mutableStateOf<Popup?>(null)
        private set

    class Popup(val title: String, val session: GeckoSession)

    private val buttons = HashMap<String, WebExtension.Action>()
    private var app: Context? = null
    private var browser: Browser? = null

    private val controller get() = Gecko.runtime(app!!).webExtensionController

    fun start(context: Context, b: Browser) {
        app = context.applicationContext
        browser = b
        controller.promptDelegate = installPrompts
        controller.ensureBuiltIn(UBLOCK_URI, UBLOCK_ID).then(
            object : GeckoResult.OnValueListener<WebExtension, Void> {
                override fun onValue(value: WebExtension?): GeckoResult<Void>? {
                    if (value != null) {
                        wire(value)
                        controller.setAllowedInPrivateBrowsing(value, true)
                    }
                    refresh()
                    return null
                }
            },
            object : GeckoResult.OnExceptionListener<Void> {
                override fun onException(exception: Throwable): GeckoResult<Void>? {
                    Log.w("SheerExt", "uBlock Origin did not start", exception)
                    refresh()
                    return null
                }
            },
        )
    }

    fun refresh() {
        if (app == null) return
        controller.list().then(
            object : GeckoResult.OnValueListener<List<WebExtension>, Void> {
                override fun onValue(value: List<WebExtension>?): GeckoResult<Void>? {
                    val all = value.orEmpty()
                    all.forEach { wire(it) }
                    list = all
                    return null
                }
            },
        )
    }

    private fun wire(ext: WebExtension) {
        ext.setActionDelegate(actionDelegate)
        ext.tabDelegate = tabDelegate
    }

    fun nameOf(ext: WebExtension): String = ext.metaData?.name ?: ext.id

    fun isOn(ext: WebExtension): Boolean = ext.metaData?.enabled == true

    /** The extensions that have a toolbar button right now: (id, name). */
    fun withButton(): List<Pair<String, String>> {
        @Suppress("UNUSED_VARIABLE") val v = version
        return list.filter { isOn(it) && buttons[it.id]?.enabled != false && buttons.containsKey(it.id) }.map { it.id to nameOf(it) }
    }

    /** A tap on an extension's toolbar button. The extension decides: a popup, or a click it handles itself. */
    fun press(id: String) {
        buttons[id]?.click()
    }

    fun closePopup() {
        popup?.session?.close()
        popup = null
    }

    fun setOn(ext: WebExtension, on: Boolean) {
        val r = if (on) controller.enable(ext, WebExtensionController.EnableSource.USER) else controller.disable(ext, WebExtensionController.EnableSource.USER)
        r.then(
            object : GeckoResult.OnValueListener<WebExtension, Void> {
                override fun onValue(value: WebExtension?): GeckoResult<Void>? {
                    refresh()
                    return null
                }
            },
        )
    }

    fun remove(ext: WebExtension) {
        controller.uninstall(ext).then(
            object : GeckoResult.OnValueListener<Void, Void> {
                override fun onValue(value: Void?): GeckoResult<Void>? {
                    buttons.remove(ext.id)
                    refresh()
                    return null
                }
            },
        )
    }

    /** Adds an extension from an address (Mozilla's add-on site). The engine asks first what it may do: [installPrompts]. */
    fun install(url: String) {
        val b = browser ?: return
        b.toast("Getting it from Mozilla")
        controller.install(url).then(
            object : GeckoResult.OnValueListener<WebExtension, Void> {
                override fun onValue(value: WebExtension?): GeckoResult<Void>? {
                    if (value != null) wire(value)
                    refresh()
                    b.toast("Added")
                    return null
                }
            },
            object : GeckoResult.OnExceptionListener<Void> {
                override fun onException(exception: Throwable): GeckoResult<Void>? {
                    Log.w("SheerExt", "install failed", exception)
                    b.toast("Could not add it")
                    return null
                }
            },
        )
    }

    private fun popupFor(ext: WebExtension): GeckoResult<GeckoSession> {
        popup?.session?.close()
        val s = GeckoSession()
        s.open(Gecko.runtime(app!!))
        popup = Popup(nameOf(ext), s)
        return GeckoResult.fromValue(s)
    }

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            // The button as it is for the whole app. A page can give it its own look (a badge), that is not tracked.
            if (session == null) {
                buttons[extension.id] = action
                version++
            }
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = popupFor(extension)

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = popupFor(extension)
    }

    private val tabDelegate = object : WebExtension.TabDelegate {
        /** An extension opens a page (its settings, a login): it becomes a normal tab and the engine loads the page into it. */
        override fun onNewTab(extension: WebExtension, details: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? {
            closePopup()
            val b = browser ?: return null
            return GeckoResult.fromValue(b.sessionForNewTab(details.url.orEmpty(), false))
        }

        override fun onOpenOptionsPage(extension: WebExtension) {
            val url = extension.metaData?.optionsPageUrl
            if (!url.isNullOrEmpty()) {
                closePopup()
                browser?.newTab(url, false)
            }
        }
    }

    /** A short, plain description of what a permission lets an extension do. */
    private fun describe(permission: String): String = when (permission) {
        "<all_urls>" -> "Read and change every site you open"
        "tabs" -> "See which pages you have open"
        "webRequest", "webRequestBlocking" -> "Watch, and block, what pages load"
        "webNavigation" -> "See where you go on the web"
        "storage", "unlimitedStorage" -> "Keep its own settings on the phone"
        "cookies" -> "Read and change cookies"
        "clipboardWrite" -> "Copy text for you"
        "clipboardRead" -> "Read what you copied"
        "notifications" -> "Show notifications"
        "contextMenus" -> "Add items to the long-press menu"
        "downloads" -> "Save files"
        "privacy" -> "Change privacy settings"
        "proxy" -> "Control how pages connect"
        "nativeMessaging" -> "Talk to other apps on the phone"
        "activeTab" -> "Work on the page you are looking at, when you ask"
        "scripting" -> "Run its own code in pages"
        else -> permission
    }

    private val installPrompts = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollection: Array<out String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse>? {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            val lines = (permissions.map { describe(it) } + origins.map { "Work on: $it" }).distinct()
            val text = if (lines.isEmpty()) "It asks for no special access." else "It can:\n" + lines.joinToString("\n") { "• $it" }
            PageAsks.show(
                Ask("Add ${nameOf(extension)}?", text, "Add", "Cancel") { yes, _, _ ->
                    result.complete(WebExtension.PermissionPromptResponse(yes, false, false))
                },
            )
            return result
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<out String>,
            newOrigins: Array<out String>,
            newDataCollection: Array<out String>,
        ): GeckoResult<AllowOrDeny>? {
            val result = GeckoResult<AllowOrDeny>()
            val lines = (newPermissions.map { describe(it) } + newOrigins.map { "Work on: $it" }).distinct()
            PageAsks.show(
                Ask("Update ${nameOf(extension)}?", "It now also wants to:\n" + lines.joinToString("\n") { "• $it" }, "Update", "Not now") { yes, _, _ ->
                    result.complete(if (yes) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                },
            )
            return result
        }

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollection: Array<out String>,
        ): GeckoResult<AllowOrDeny>? {
            val result = GeckoResult<AllowOrDeny>()
            val lines = (permissions.map { describe(it) } + origins.map { "Work on: $it" }).distinct()
            PageAsks.show(
                Ask("${nameOf(extension)} wants more access", lines.joinToString("\n") { "• $it" }, "Allow", "Block") { yes, _, _ ->
                    result.complete(if (yes) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                },
            )
            return result
        }
    }
}
