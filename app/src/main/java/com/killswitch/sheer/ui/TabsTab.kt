package com.killswitch.sheer.ui

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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.killswitch.sheer.Browser
import com.killswitch.sheer.UrlInput

/** Every open page. There is always at least one (closing the last one leaves a fresh start page), so this list is never empty. */
@Composable
fun TabsTab(browser: Browser, pad: PaddingValues, seen: MutableSet<Any>, onOpen: () -> Unit) {
    val tabs = browser.tabs
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Column(Modifier.fillMaxWidth().staggerIn(0, seen.add("tabs-head")), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageTitle("Tabs", if (tabs.size == 1) "1 page open" else "${tabs.size} pages open")
                Box(Modifier.padding(start = 4.dp)) { AccentPill("New tab", { browser.newTab(); onOpen() }) }
                Row(Modifier.pressable { browser.newTab(incognito = true); onOpen() }.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Private, Aura.AccentText, Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    BasicText("New private tab", style = ts(15f, Aura.AccentText, FontWeight.Bold))
                }
                if (tabs.any { it.incognito }) {
                    Row(Modifier.pressable { browser.closePrivate() }.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.Close, Aura.Muted, Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        BasicText("Close all private tabs", style = ts(15f, Aura.Muted, FontWeight.Bold))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        items(tabs.toList(), key = { it.id }) { t ->
            val index = tabs.indexOf(t) + 1
            val selected = t.id == browser.selectedId
            val host = UrlInput.host(t.url)
            Row(
                Modifier.fillMaxWidth().staggerIn(index, seen.add("tab-${t.id}")).topHairline(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).pressable { browser.select(t.id); onOpen() }.padding(vertical = 16.dp, horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (t.incognito) {
                            Icon(Ic.Private, Aura.Muted, Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                        }
                        BasicText(
                            t.title.ifEmpty { host.ifEmpty { if (t.incognito) "New private tab" else "New tab" } },
                            style = ts(16f, if (selected) Aura.AccentText else Aura.Text, FontWeight.Bold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicText(
                        host.ifEmpty { "Start page" },
                        style = ts(13f, Aura.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.pressable { browser.close(t.id) }.padding(14.dp)) {
                    Icon(Ic.Close, Aura.Muted, Modifier.size(18.dp))
                }
            }
        }
    }
}
