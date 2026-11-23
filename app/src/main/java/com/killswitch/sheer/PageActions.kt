package com.killswitch.sheer

import com.killswitch.sheer.ui.Ask
import com.killswitch.sheer.ui.Choice
import com.killswitch.sheer.ui.PageAsks
import org.mozilla.geckoview.GeckoSession

/** The menus a person opens on a page: the three-dot menu and the long press on a link or image. Both are a list in the glass question dialog. */
object PageActions {
    /** [onFind] opens the find bar, which lives in the Browse screen. */
    fun pageMenu(browser: Browser, tab: BTab, onFind: () -> Unit) {
        val rows = listOf(Choice("share", "Share"), Choice("find", "Find in page"), Choice("copy", "Copy link"))
        val title = tab.title.ifEmpty { UrlInput.host(tab.url) }.take(80)
        PageAsks.show(
            Ask(title, UrlInput.host(tab.url), no = "Close", choices = rows) { yes, _, picked ->
                if (yes) {
                    when (picked.firstOrNull()) {
                        "share" -> browser.share(tab.url)
                        "find" -> onFind()
                        "copy" -> browser.copy(tab.url, "Link")
                        else -> {}
                    }
                }
            },
        )
    }

    /** What the long press landed on. A plain piece of text gets no menu. */
    fun contextMenu(browser: Browser, tab: BTab, el: GeckoSession.ContentDelegate.ContextElement) {
        val link = el.linkUri.orEmpty()
        val image = if (el.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) el.srcUri.orEmpty() else ""
        val rows = ArrayList<Choice>()
        if (link.isNotEmpty()) {
            rows.add(Choice("tab", "Open in new tab"))
            rows.add(Choice("copylink", "Copy link"))
            rows.add(Choice("sharelink", "Share link"))
        }
        if (image.isNotEmpty()) {
            rows.add(Choice("save", "Save image"))
            rows.add(Choice("imgtab", "Open image in new tab"))
            rows.add(Choice("copyimg", "Copy image address"))
        }
        if (rows.isEmpty()) return
        val title = (if (link.isNotEmpty()) el.linkText.orEmpty().ifBlank { UrlInput.host(link) } else el.title.orEmpty().ifBlank { "Image" }).take(80)
        PageAsks.show(
            Ask(title, link.ifEmpty { image }, no = "Close", choices = rows) { yes, _, picked ->
                if (yes) {
                    when (picked.firstOrNull()) {
                        "tab" -> browser.newTab(link, tab.incognito)
                        "copylink" -> browser.copy(link, "Link")
                        "sharelink" -> browser.share(link)
                        "save" -> browser.saveImage(image)
                        "imgtab" -> browser.newTab(image, tab.incognito)
                        "copyimg" -> browser.copy(image, "Address")
                        else -> {}
                    }
                }
            },
        )
    }
}
