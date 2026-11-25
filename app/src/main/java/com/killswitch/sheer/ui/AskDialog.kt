package com.killswitch.sheer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killswitch.sheer.glass.LiquidPanel
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch

/** One row of a pick list (a <select> from a page). A header is a group title that cannot be picked. */
class Choice(val id: String, val label: String, val enabled: Boolean = true, val selected: Boolean = false, val header: Boolean = false)

/**
 * One question a page or the engine puts to the person: a JavaScript dialog, a permission, a <select> list, a typed value.
 * [onAnswer] gets whether they said yes, the text they typed, and the ids they picked.
 */
class Ask(
    val title: String,
    val message: String = "",
    val yes: String = "OK",
    val no: String? = null,
    /** Not null: a text field that starts with this text. */
    val input: String? = null,
    /** Not null: rows to pick from. One pick answers straight away unless [multiple]. */
    val choices: List<Choice>? = null,
    val multiple: Boolean = false,
    val onAnswer: (yes: Boolean, text: String, picked: List<String>) -> Unit,
)

/** The questions waiting for an answer, shown one at a time. */
object PageAsks {
    var current by mutableStateOf<Ask?>(null)
        private set
    private val waiting = ArrayDeque<Ask>()

    fun show(a: Ask) {
        if (current == null) current = a else waiting.addLast(a)
    }

    /** The question was answered, or the page took it back. */
    fun finish(a: Ask) {
        if (current === a) current = waiting.removeFirstOrNull() else waiting.remove(a)
    }
}

/** A question over the whole app, in library glass, the same recipe as [NoticeDialog]. Tapping outside or going back answers "no". */
@Composable
fun AskDialog(backdrop: Backdrop, ask: Ask) {
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(ask.input ?: "") }
    val picked = remember {
        mutableStateListOf<String>().also { l ->
            if (ask.multiple) ask.choices?.forEach { if (it.selected && !it.header) l.add(it.id) }
        }
    }
    var answered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun answer(yes: Boolean, one: String? = null) {
        if (answered) return
        answered = true
        // Typing in the field took keyboard focus off the page. Give it up first so the page is itself again when the answer arrives.
        focusManager.clearFocus()
        keyboard?.hide()
        scope.launch {
            enter.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
            ask.onAnswer(yes, text, if (one != null) listOf(one) else picked.toList())
            PageAsks.finish(ask)
        }
    }
    // With only an OK button, leaving the dialog any other way means OK too.
    val leaveIsYes = ask.no == null
    BackHandler { answer(leaveIsYes) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }.background(Color.Black.copy(alpha = .45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { answer(leaveIsYes) },
        )
        LiquidPanel(
            backdrop,
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp)
                .graphicsLayer { alpha = enter.value; scaleX = .92f + .08f * enter.value; scaleY = .92f + .08f * enter.value }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            corner = 36.dp,
            contentPadding = PaddingValues(24.dp),
        ) {
            BasicText(ask.title, style = ts(20f, weight = FontWeight.ExtraBold))
            if (ask.message.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Box(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) { BasicText(ask.message, style = ts(15f, Aura.Muted)) }
            }
            if (ask.input != null) {
                Spacer(Modifier.size(14.dp))
                Box(Modifier.fillMaxWidth().background(Aura.overlay(.10f), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    BasicTextField(
                        text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = ts(16f),
                        cursorBrush = SolidColor(Aura.Accent),
                    )
                }
            }
            val rows = ask.choices
            if (rows != null) {
                Spacer(Modifier.size(12.dp))
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    rows.forEach { c ->
                        if (c.header) {
                            BasicText(c.label, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp), style = ts(13f, Aura.Muted, FontWeight.Bold))
                        } else {
                            val on = if (ask.multiple) c.id in picked else c.selected
                            val tap = if (!c.enabled) Modifier else Modifier.pressable {
                                if (ask.multiple) { if (on) picked.remove(c.id) else picked.add(c.id) } else answer(true, c.id)
                            }
                            Row(
                                Modifier.fillMaxWidth().topHairline().then(tap).padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicText(
                                    c.label, modifier = Modifier.weight(1f),
                                    style = ts(16f, if (c.enabled) Aura.Text else Aura.Muted, if (on) FontWeight.ExtraBold else FontWeight.Medium),
                                )
                                if (on) Icon(Ic.Check, Aura.Accent, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(20.dp))
            if (rows != null && !ask.multiple) {
                BigButton(ask.no ?: "Cancel", { answer(false) }, Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (ask.no != null) BigButton(ask.no, { answer(false) }, Modifier.weight(1f))
                    BigButton(ask.yes, { answer(true) }, Modifier.weight(1f), accent = true)
                }
            }
        }
    }
}
