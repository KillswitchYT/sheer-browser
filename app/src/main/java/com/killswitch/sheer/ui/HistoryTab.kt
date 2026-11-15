package com.killswitch.sheer.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.killswitch.sheer.Browser
import com.killswitch.sheer.History
import com.killswitch.sheer.UrlInput
import com.killswitch.sheer.glass.LiquidField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "Today", "Yesterday", or the date, for the heading above a day's pages. */
private fun dayLabel(t: Long): String {
    fun midnight(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val days = (midnight(System.currentTimeMillis()) - midnight(t)) / 86_400_000L
    return when (days) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(t))
    }
}

private fun timeLabel(t: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

/**
 * Every page visited in normal tabs, newest first, kept on the phone. Private tabs never appear here. Tap a row to open it in the
 * current tab, the cross removes one, and "Clear history" asks twice.
 */
@Composable
fun HistoryTab(browser: Browser, pad: PaddingValues, seen: MutableSet<Any>, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<History.Visit>?>(null) } // null while the first read is running
    var confirm by remember { mutableStateOf(false) }
    val version = History.version

    LaunchedEffect(query, version) { rows = withContext(Dispatchers.IO) { History.query(ctx, query.trim(), 400) } }
    LaunchedEffect(confirm) { if (confirm) { delay(4000); confirm = false } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Column(Modifier.fillMaxWidth().staggerIn(0, seen.add("hist-head")), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageTitle("History", "Pages from normal tabs, kept on this phone only")
                LiquidField(glassBackdrop(), Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Ic.Search, Aura.Muted, Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    BasicTextField(
                        query, { query = it }, Modifier.weight(1f), singleLine = true, textStyle = ts(15f),
                        cursorBrush = SolidColor(Aura.Accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        decorationBox = { inner ->
                            if (query.isEmpty()) BasicText("Search your history", style = ts(15f, Aura.Muted))
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        Box(Modifier.pressable { query = "" }.padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) { Icon(Ic.Close, Aura.Muted, Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        val list = rows
        when {
            // Loading: quiet placeholder rows in the shape of the real ones.
            list == null -> items(3) {
                Column(Modifier.fillMaxWidth().topHairline().padding(vertical = 16.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(.6f).height(16.dp).background(Aura.overlay(.10f), RoundedCornerShape(6.dp)))
                    Box(Modifier.fillMaxWidth(.35f).height(12.dp).background(Aura.overlay(.07f), RoundedCornerShape(6.dp)))
                }
            }
            list.isEmpty() && query.isBlank() -> item {
                EmptyState(Ic.Clock, "No history yet", "Pages you visit in normal tabs show up here. Private tabs never do.", "Open a page", onOpen)
            }
            list.isEmpty() -> item { NoResults("Nothing in your history matches \"${query.trim()}\".") }
            else -> {
                itemsIndexed(list, key = { _, v -> v.url }) { index, v ->
                    val newDay = index == 0 || dayLabel(list[index - 1].last) != dayLabel(v.last)
                    Column(Modifier.fillMaxWidth()) {
                        if (newDay) BasicText(dayLabel(v.last), Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp), style = ts(13f, Aura.Muted, FontWeight.ExtraBold))
                        Row(Modifier.fillMaxWidth().topHairline(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).pressable { browser.go(v.url); onOpen() }.padding(vertical = 14.dp, horizontal = 4.dp)) {
                                BasicText(
                                    v.title.ifEmpty { UrlInput.host(v.url) },
                                    style = ts(16f, weight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                BasicText(
                                    "${UrlInput.host(v.url)}  ·  ${timeLabel(v.last)}",
                                    style = ts(13f, Aura.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(Modifier.pressable { History.delete(ctx, v.url) }.padding(14.dp)) { Icon(Ic.Close, Aura.Muted, Modifier.size(18.dp)) }
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().topHairline().pressable { if (confirm) { History.clear(ctx); confirm = false } else confirm = true }.padding(vertical = 18.dp, horizontal = 4.dp)) {
                        BasicText(
                            if (confirm) "Tap again to delete all history" else "Clear history",
                            style = ts(15f, if (confirm) Aura.Rose else Aura.Muted, FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}
