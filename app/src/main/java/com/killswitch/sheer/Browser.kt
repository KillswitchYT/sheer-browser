package com.killswitch.sheer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * One open page. The Gecko session is only made the first time the page is wanted, so a tab restored from the last run costs nothing
 * until it is opened. A tab with no session is the start page.
 */
/** [incognito] tabs keep nothing: no history, no cookies, not even the list of open tabs on the next start. */
class BTab(val id: Int, url: String, title: String, val incognito: Boolean = false) {
    var url by mutableStateOf(url)
    var title by mutableStateOf(title)
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var secure by mutableStateOf(false)
    var crashed by mutableStateOf(false)
    var session by mutableStateOf<GeckoSession?>(null)

    /** When the page process last stopped under this tab, to tell a one-off from a loop. */
    var lastStop = 0L
}

/** All open tabs and the one being shown. Lives as long as the process, so the pages survive the screen being rebuilt. */
class Browser(private val app: Context) {
    val tabs = mutableStateListOf<BTab>()
    var selectedId by mutableIntStateOf(-1)
    private var nextId = 1

    /** The view currently showing a page, so a session can be let go of before it is closed. */
    var view: GeckoView? = null

    val current: BTab? get() = tabs.firstOrNull { it.id == selectedId }

    private val main = Handler(Looper.getMainLooper())

    init {
        restore()
        current?.let { if (it.url.isNotEmpty()) ensure(it) } // the tab that was open last time shows its page straight away
    }

    private fun restore() {
        try {
            val raw = Settings.str(app, "tabs")
            if (raw.isNotEmpty()) {
                val o = JSONObject(raw)
                val a = o.getJSONArray("t")
                for (i in 0 until a.length()) {
                    val t = a.getJSONObject(i)
                    tabs.add(BTab(nextId++, t.optString("u"), t.optString("n")))
                }
                if (tabs.isNotEmpty()) selectedId = tabs[o.optInt("s", 0).coerceIn(0, tabs.size - 1)].id
            }
        } catch (e: Exception) {
            tabs.clear()
        }
        if (tabs.isEmpty()) {
            val t = BTab(nextId++, "", "")
            tabs.add(t)
            selectedId = t.id
        }
    }

    private val saveLater = Runnable { save() }

    /** Saves a moment after the last change, so a crash or a kill never loses the open tabs. */
    private fun scheduleSave() {
        main.removeCallbacks(saveLater)
        main.postDelayed(saveLater, 1500)
    }

    /** Written a moment after every change, and when the app goes to the background. Only the address and title of each tab. */
    fun save() {
        val keep = tabs.filter { !it.incognito } // private tabs are never written down
        val a = JSONArray()
        keep.forEach { a.put(JSONObject().put("u", it.url).put("n", it.title)) }
        val s = keep.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        Settings.setStr(app, "tabs", JSONObject().put("t", a).put("s", s).toString())
    }

    fun newTab(url: String? = null, incognito: Boolean = false): BTab {
        val t = BTab(nextId++, url.orEmpty(), "", incognito)
        tabs.add(t)
        selectedId = t.id
        if (url != null) ensure(t)
        scheduleSave()
        return t
    }

    fun select(id: Int) {
        if (tabs.none { it.id == id }) return
        selectedId = id
        showCurrent()
        scheduleSave()
    }

    /** A tab that was restored (or whose page process stopped while it was in the background) gets its page when it is shown. */
    private fun showCurrent() {
        val t = current ?: return
        if (t.session == null && t.url.isNotEmpty() && !t.crashed) ensure(t)
    }

    fun close(id: Int) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = tabs.removeAt(i)
        dispose(t)
        if (tabs.isEmpty()) {
            val n = BTab(nextId++, "", "")
            tabs.add(n)
            selectedId = n.id
        } else if (t.id == selectedId) {
            selectedId = tabs[i.coerceAtMost(tabs.size - 1)].id
            showCurrent()
        }
        scheduleSave()
    }

    private fun dispose(t: BTab) {
        val s = t.session ?: return
        if (view?.session === s) view?.releaseSession()
        s.navigationDelegate = null
        s.progressDelegate = null
        s.contentDelegate = null
        s.historyDelegate = null
        s.close()
        t.session = null
    }

    /** Make the page behind this tab, the first time it is needed. */
    fun ensure(t: BTab): GeckoSession {
        t.session?.let { return it }
        val s = makeSession(t.incognito)
        attach(t, s)
        s.open(Gecko.runtime(app))
        t.session = s
        if (t.url.isNotEmpty()) s.loadUri(t.url)
        return s
    }

    /** A private session keeps its cookies and cache in memory only, and the engine forgets them when the last private page closes. */
    private fun makeSession(incognito: Boolean): GeckoSession =
        if (incognito) GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(true).build()) else GeckoSession()

    fun closePrivate() { tabs.filter { it.incognito }.map { it.id }.forEach { close(it) } }

    /** Address bar: what was typed, turned into a page or a search. */
    fun go(input: String) {
        val t = current ?: return
        val url = UrlInput.resolve(input, UrlInput.ENGINES[Settings.engine(app)]) ?: return
        val had = t.session != null
        t.url = url
        t.title = ""
        val s = ensure(t)
        if (had) s.loadUri(url)
        scheduleSave()
    }

    /** A link from another app. */
    fun openIncoming(url: String) {
        if (!UrlInput.isWeb(url)) return
        newTab(url.trim())
    }

    fun back() { current?.session?.goBack() }
    fun forward() { current?.session?.goForward() }
    fun reload() { current?.session?.reload() }
    fun stop() { current?.session?.stop() }

    /** After the page process died: throw the session away and build a fresh one on the same address. */
    fun recover(t: BTab) {
        dispose(t)
        t.crashed = false
        t.loading = false
        t.progress = 0
        if (t.url.isNotEmpty()) ensure(t)
    }

    /**
     * The process that draws a page died (a crash, or Android reclaiming memory). The first time, the page is quietly loaded again;
     * a second stop within 30 seconds shows the "page crashed" screen instead, so it can never loop. Every stop is written to the log
     * that Settings, About, Recent stops shows.
     */
    private fun stopped(t: BTab, how: String) {
        t.loading = false
        t.progress = 0
        CrashLog.event(app, "page process $how on ${if (t.incognito) "a private tab" else UrlInput.host(t.url).ifEmpty { "a blank tab" }}")
        val now = SystemClock.elapsedRealtime()
        val repeat = t.lastStop != 0L && now - t.lastStop < 30_000
        t.lastStop = now
        main.post { // not inside the engine's own callback
            dispose(t)
            if (t.id != selectedId) return@post // a background tab loads again when it is shown
            if (repeat) t.crashed = true else if (t.url.isNotEmpty()) ensure(t)
        }
    }

    private fun attach(t: BTab, s: GeckoSession) {
        // History is only recorded for normal tabs. A private tab gets no delegate at all, so there is nothing to record and links never
        // show as visited.
        if (!t.incognito) {
            s.historyDelegate = object : GeckoSession.HistoryDelegate {
                override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean>? {
                    val top = flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL != 0
                    val hop = flags and (GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE or GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT) != 0
                    val failed = flags and GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR != 0
                    if (top && !hop && !failed && UrlInput.isWeb(url)) History.record(app, url, "")
                    return GeckoResult.fromValue(true)
                }

                override fun getVisited(session: GeckoSession, urls: Array<String>): GeckoResult<BooleanArray>? {
                    val r = GeckoResult<BooleanArray>()
                    History.visited(app, urls) { r.complete(it) }
                    return r
                }
            }
        }
        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (url != null) { t.url = url; scheduleSave() }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { t.canBack = canGoBack }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { t.canForward = canGoForward }

            // A link that opens a new window (target=_blank, window.open): it becomes a new tab. Gecko opens the session we hand back.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val nt = BTab(nextId++, uri, "", t.incognito)
                val ns = makeSession(t.incognito)
                attach(nt, ns)
                nt.session = ns
                tabs.add(nt)
                selectedId = nt.id
                return GeckoResult.fromValue(ns)
            }
        }
        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) { t.loading = true; t.progress = 5; t.crashed = false }
            override fun onPageStop(session: GeckoSession, success: Boolean) { t.loading = false; t.progress = 0 }
            override fun onProgressChange(session: GeckoSession, progress: Int) { t.progress = progress }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                t.secure = securityInfo.isSecure
            }
        }
        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                t.title = title.orEmpty()
                if (!t.incognito) { History.setTitle(app, t.url, t.title); scheduleSave() }
            }
            override fun onCrash(session: GeckoSession) { stopped(t, "crashed") }
            override fun onKill(session: GeckoSession) { stopped(t, "was killed") }
        }
    }
}
