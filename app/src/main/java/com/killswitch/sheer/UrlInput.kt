package com.killswitch.sheer

import java.net.URLEncoder

/** What to load for whatever was typed in the address bar. Plain Kotlin on purpose: tools/EngineTest.kt runs it on a normal JVM. */
object UrlInput {
    class Engine(val name: String, val template: String)

    /** The first one is the default. */
    val ENGINES = listOf(
        Engine("Google", "https://www.google.com/search?q=%s"),
        Engine("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        Engine("Brave Search", "https://search.brave.com/search?q=%s"),
        Engine("Startpage", "https://www.startpage.com/do/search?q=%s"),
    )

    private val WEB = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
    private val HOST = Regex("^([A-Za-z0-9-]+\\.)+[A-Za-z][A-Za-z0-9-]+(:\\d{1,5})?([/?#]\\S*)?$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?([/?#]\\S*)?$")
    private val LOCAL = Regex("^localhost(:\\d{1,5})?([/?#]\\S*)?$", RegexOption.IGNORE_CASE)
    private val HOSTPART = Regex("^https?://([^/?#]+)", RegexOption.IGNORE_CASE)

    /**
     * A web address stays as typed, a bare host becomes https (an IP address or localhost becomes http, they have no certificate),
     * and anything else is a search. Other schemes (javascript:, data:, file:) are searched for, never run. Null for blank input.
     */
    fun resolve(input: String, engine: Engine): String? {
        val t = input.trim()
        if (t.isEmpty()) return null
        if (WEB.matches(t)) return t
        if (' ' !in t) {
            if (LOCAL.matches(t) || IPV4.matches(t)) return "http://$t"
            if (HOST.matches(t)) return "https://$t"
        }
        return engine.template.replace("%s", URLEncoder.encode(t, "UTF-8"))
    }

    /** The quiet line for the address bar: the host without "www.", or the text itself when it is not a web page. */
    fun host(url: String): String {
        if (url.isBlank() || url == "about:blank") return ""
        val m = HOSTPART.find(url) ?: return url
        var h = m.groupValues[1].substringAfterLast('@')
        if (h.startsWith("www.", ignoreCase = true)) h = h.substring(4)
        return h
    }

    /** Only web addresses may be opened from outside the app (a link tapped in another app). */
    fun isWeb(url: String?): Boolean = url != null && WEB.matches(url.trim())
}
