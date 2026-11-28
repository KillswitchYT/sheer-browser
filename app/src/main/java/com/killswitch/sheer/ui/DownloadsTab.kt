package com.killswitch.sheer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.killswitch.sheer.Browser
import com.killswitch.sheer.DownloadLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

private fun sizeLabel(b: Long): String = when {
    b < 1024L -> "$b B"
    b < 1024L * 1024 -> String.format(Locale.US, "%.0f kB", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f MB", b / 1048576.0)
    else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
}

private fun dayHead(time: Long): String {
    fun midnight(t: Long): Long = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val days = (midnight(System.currentTimeMillis()) - midnight(time)) / 86_400_000L
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 8 -> "Last 7 days"
        else -> "Older"
    }
}

private fun openFile(ctx: Context, d: DownloadLog.Item, browser: Browser) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(d.uri), d.mime.ifEmpty { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) { browser.toast("No app here can open this") } catch (e: Exception) { browser.toast("Could not open it") }
}

private fun share(ctx: Context, d: DownloadLog.Item, browser: Browser) {
    val send = Intent(Intent.ACTION_SEND).setType(d.mime.ifEmpty { "*/*" }).putExtra(Intent.EXTRA_STREAM, Uri.parse(d.uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { browser.toast("Could not share it") }
}

private fun options(ctx: Context, d: DownloadLog.Item, browser: Browser) {
    val rows = listOf(Choice("open", "Open"), Choice("share", "Share"), Choice("remove", "Remove from the list"), Choice("delete", "Delete the file"))
    PageAsks.show(
        Ask(d.name.take(80), sizeLabel(d.size), no = "Close", choices = rows) { yes, _, picked ->
            if (yes) {
                when (picked.firstOrNull()) {
                    "open" -> openFile(ctx, d, browser)
                    "share" -> share(ctx, d, browser)
                    "remove" -> DownloadLog.remove(ctx, d.id)
                    "delete" -> Thread {
                        runCatching { ctx.contentResolver.delete(Uri.parse(d.uri), null, null) }
                        DownloadLog.remove(ctx, d.id)
                    }.start()
                    else -> {}
                }
            }
        },
    )
}

/** What was downloaded, newest first, by day. The files are in the phone's Downloads folder; a proper download manager comes later. */
@Composable
fun DownloadsTab(browser: Browser, pad: PaddingValues, seen: MutableSet<Any>, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<DownloadLog.Item>?>(null) }
    val version = DownloadLog.version
    LaunchedEffect(query, kind, version) { rows = withContext(Dispatchers.IO) { DownloadLog.query(ctx, query.trim(), kind, 400) } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        item(key = "head") {
            Column {
                PageTitle("Downloads", "Saved in your phone's Downloads folder")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, label) in listOf(0 to "All", 1 to "Images", 2 to "Videos", 3 to "Documents", 4 to "Other")) {
                        val on = kind == k
                        Box(
                            Modifier.background(if (on) Aura.overlay(.16f) else Color.Transparent, RoundedCornerShape(50)).pressable { kind = k }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        ) { BasicText(label, style = ts(14f, if (on) Aura.Text else Aura.Muted, FontWeight.Bold)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().background(Aura.overlay(.08f), RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.Search, Aura.Muted, Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    BasicTextField(
                        query, { query = it }, Modifier.weight(1f), singleLine = true, textStyle = ts(15f),
                        cursorBrush = SolidColor(Aura.Accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        decorationBox = { inner ->
                            if (query.isEmpty()) BasicText("Search your downloads", style = ts(15f, Aura.Muted))
                            inner()
                        },
                    )
                }
            }
        }
        val list = rows
        when {
            list == null -> item { Spacer(Modifier.height(40.dp)) }
            list.isEmpty() && query.isBlank() && kind == 0 -> item {
                EmptyState(Ic.Download, "Nothing downloaded yet", "Files you download are saved in your phone's Downloads folder and listed here.", "Open a page", onOpen)
            }
            list.isEmpty() -> item { NoResults("Nothing here matches.") }
            else -> {
                var last = ""
                list.forEach { d ->
                    val head = dayHead(d.time)
                    if (head != last) {
                        last = head
                        item(key = "day-$head") { BasicText(head, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp), style = ts(15f, Aura.Muted, FontWeight.ExtraBold)) }
                    }
                    item(key = d.id) {
                        Row(Modifier.fillMaxWidth().topHairline(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.weight(1f).pressable { openFile(ctx, d, browser) }.padding(vertical = 14.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Ic.File, Aura.Muted, Modifier.size(26.dp))
                                Spacer(Modifier.size(14.dp))
                                Column(Modifier.weight(1f)) {
                                    BasicText(d.name, style = ts(16f, weight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    BasicText(
                                        sizeLabel(d.size) + if (d.source.isNotEmpty()) "  \u2022  ${d.source}" else "",
                                        style = ts(13f, Aura.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Box(Modifier.semantics { contentDescription = "Download options" }.pressable { options(ctx, d, browser) }.padding(14.dp)) {
                                Icon(Ic.More, Aura.Muted, Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
