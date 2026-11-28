package com.killswitch.sheer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.net.URLConnection
import java.net.URLDecoder

/** Saves what a page hands over as a file in the phone's Downloads folder. No download manager, no notification, no extra service. */
object Downloads {
    /** A link or button on a page that sends a file. */
    fun save(browser: Browser, app: Context, response: WebResponse) {
        val body = response.body
        if (body == null) {
            browser.toast("Nothing to download")
            return
        }
        val name = fileName(response.headers, response.uri)
        browser.toast("Downloading $name")
        val source = hostOf(response.uri)
        Thread { write(browser, app, name, mimeType(response.headers, name), body, source) }.start()
    }

    /** An image from the long-press menu: fetched with the engine's own network stack (same cookies), then saved. */
    fun saveUrl(browser: Browser, app: Context, url: String) {
        browser.toast("Downloading image")
        GeckoWebExecutor(Gecko.runtime(app)).fetch(WebRequest.Builder(url).build()).then(
            object : GeckoResult.OnValueListener<WebResponse, Void> {
                override fun onValue(value: WebResponse?): GeckoResult<Void>? {
                    val body = value?.body
                    if (value == null || body == null || value.statusCode !in 200..299) {
                        browser.toast("Download failed")
                        return null
                    }
                    val name = fileName(value.headers, url)
                    val source = hostOf(url)
                    Thread { write(browser, app, name, mimeType(value.headers, name), body, source) }.start()
                    return null
                }
            },
            object : GeckoResult.OnExceptionListener<Void> {
                override fun onException(exception: Throwable): GeckoResult<Void>? {
                    browser.toast("Download failed")
                    return null
                }
            },
        )
    }

    private fun hostOf(u: String): String = runCatching { Uri.parse(u).host }.getOrNull().orEmpty().removePrefix("www.")

    private fun write(browser: Browser, app: Context, name: String, mime: String, body: InputStream, source: String) {
        try {
            body.use { input ->
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val res = app.contentResolver
                    val uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("no place to save")
                    try {
                        val size = res.openOutputStream(uri)!!.use { out -> input.copyTo(out) }
                        res.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                        // The phone may have renamed it ("name (1).pdf") if there already was one.
                        val shown = res.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
                        DownloadLog.add(app, shown, mime, size, uri.toString(), source)
                    } catch (e: Exception) {
                        res.delete(uri, null, null)
                        throw e
                    }
                } else {
                    val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IllegalStateException("no folder")
                    val f = File(dir, name)
                    val size = f.outputStream().use { out -> input.copyTo(out) }
                    DownloadLog.add(app, name, mime, size, Uri.fromFile(f).toString(), source)
                }
            }
            browser.toast("Saved to Downloads: $name")
        } catch (e: Exception) {
            browser.toast("Download failed")
        }
    }

    private fun header(h: Map<String, String>?, key: String): String? = h?.entries?.firstOrNull { it.key.equals(key, true) }?.value

    private fun fileName(h: Map<String, String>?, uri: String): String {
        val cd = header(h, "content-disposition").orEmpty()
        var n: String? = Regex("filename\\*=(?:UTF-8|utf-8)''([^;]+)").find(cd)?.groupValues?.get(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        if (n == null) n = Regex("filename=\"?([^\";]+)\"?").find(cd)?.groupValues?.get(1)
        if (n.isNullOrBlank()) n = Uri.parse(uri).lastPathSegment
        val clean = (n ?: "download").replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimStart('.')
        return clean.ifEmpty { "download" }.take(120)
    }

    private fun mimeType(h: Map<String, String>?, name: String): String {
        val ct = header(h, "content-type")?.substringBefore(';')?.trim().orEmpty()
        if (ct.isNotEmpty() && ct != "application/octet-stream") return ct
        return URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
    }
}
