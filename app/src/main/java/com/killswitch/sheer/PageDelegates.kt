package com.killswitch.sheer

import android.content.Context
import android.net.Uri
import com.killswitch.sheer.ui.Ask
import com.killswitch.sheer.ui.Choice
import com.killswitch.sheer.ui.PageAsks
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate

/** The site name to show in a question, "This page" when there is none. */
private fun hostOf(url: String?): String =
    runCatching { Uri.parse(url ?: "").host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotEmpty() } ?: "This page"

/**
 * What a page asks through JavaScript and forms: alert, confirm, prompt, the "leave this page?" question, <select> lists, date and colour
 * pickers, file choosers, and popup windows. Without this the engine answers none of them, and a <select> does not even open.
 * Each one becomes an [Ask] in a glass dialog; the engine is told the answer when the person gives it.
 */
class PagePrompts(private val app: Context, private val browser: Browser, private val tab: BTab) : PromptDelegate {
    private val site get() = hostOf(tab.url)
    private fun says(title: String?) = if (title.isNullOrEmpty()) "$site says" else title

    private fun question(
        prompt: PromptDelegate.BasePrompt,
        title: String,
        message: String = "",
        yes: String = "OK",
        no: String? = null,
        input: String? = null,
        choices: List<Choice>? = null,
        multiple: Boolean = false,
        answer: (Boolean, String, List<String>) -> PromptDelegate.PromptResponse,
    ): GeckoResult<PromptDelegate.PromptResponse> {
        val result = GeckoResult<PromptDelegate.PromptResponse>()
        var done = false
        val kind = prompt.javaClass.simpleName
        android.util.Log.w("SheerPrompt", "asking: $kind")
        val shown = Ask(title, message, yes, no, input, choices, multiple) { y, text, picked ->
            if (!done) {
                done = true
                browser.view?.requestFocus()
                runCatching { result.complete(answer(y, text, picked)) }
                    .onSuccess { android.util.Log.w("SheerPrompt", "answered: $kind yes=$y") }
                    .onFailure { android.util.Log.w("SheerPrompt", "answer failed: $kind", it) }
            } else {
                android.util.Log.w("SheerPrompt", "answer too late: $kind")
            }
        }
        // The page can take the question back (it navigated away): then the dialog goes too and nothing is sent.
        prompt.delegate = object : PromptDelegate.PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: PromptDelegate.BasePrompt) {
                android.util.Log.w("SheerPrompt", "the page took it back: $kind")
                done = true
                PageAsks.finish(shown)
            }

            override fun onPromptUpdate(prompt: PromptDelegate.BasePrompt) {}
        }
        PageAsks.show(shown)
        return result
    }

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptDelegate.PromptResponse>? =
        question(prompt, says(prompt.title), prompt.message.orEmpty()) { _, _, _ -> prompt.dismiss() }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptDelegate.PromptResponse>? =
        question(prompt, says(prompt.title), prompt.message.orEmpty(), "OK", "Cancel") { yes, _, _ ->
            prompt.confirm(if (yes) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE)
        }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptDelegate.PromptResponse>? =
        question(prompt, says(prompt.title), prompt.message.orEmpty(), "OK", "Cancel", input = prompt.defaultValue.orEmpty()) { yes, text, _ ->
            if (yes) prompt.confirm(text) else prompt.dismiss()
        }

    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt): GeckoResult<PromptDelegate.PromptResponse>? =
        question(prompt, "Leave this page?", "What you entered may not be saved.", "Leave", "Stay") { yes, _, _ ->
            prompt.confirm(if (yes) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
        }

    override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt): GeckoResult<PromptDelegate.PromptResponse>? =
        question(prompt, "Open a new window?", "$site wants to open:\n${prompt.targetUri.orEmpty()}", "Allow", "Block") { yes, _, _ ->
            prompt.confirm(if (yes) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
        }

    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt): GeckoResult<PromptDelegate.PromptResponse>? =
        question(prompt, "Send the form again?", "Reloading this page sends what you entered a second time.", "Send", "Cancel") { yes, _, _ ->
            prompt.confirm(if (yes) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
        }

    /** A <select> list. Without this the dropdown never opens. */
    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptDelegate.PromptResponse>? {
        val rows = ArrayList<Choice>()
        fun add(c: PromptDelegate.ChoicePrompt.Choice) {
            val kids = c.items
            if (kids != null && kids.isNotEmpty()) {
                rows.add(Choice(c.id.orEmpty(), c.label.orEmpty(), enabled = false, header = true))
                kids.forEach { add(it) }
            } else if (!c.separator) {
                rows.add(Choice(c.id.orEmpty(), c.label.orEmpty(), enabled = !c.disabled, selected = c.selected))
            }
        }
        prompt.choices.forEach { add(it) }
        val multiple = prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
        return question(
            prompt, says(prompt.title).takeIf { !prompt.title.isNullOrEmpty() } ?: "Choose", prompt.message.orEmpty(),
            if (multiple) "Done" else "OK", "Cancel", choices = rows, multiple = multiple,
        ) { yes, _, picked ->
            when {
                !yes || picked.isEmpty() -> prompt.dismiss()
                multiple -> prompt.confirm(picked.toTypedArray())
                else -> prompt.confirm(picked[0])
            }
        }
    }

    override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt): GeckoResult<PromptDelegate.PromptResponse>? {
        val how = when (prompt.type) {
            PromptDelegate.DateTimePrompt.Type.DATE -> "Type a date as year-month-day, for example 2026-11-20."
            PromptDelegate.DateTimePrompt.Type.TIME -> "Type a time as hour:minute, for example 14:30."
            PromptDelegate.DateTimePrompt.Type.MONTH -> "Type a month as year-month, for example 2026-11."
            PromptDelegate.DateTimePrompt.Type.WEEK -> "Type a week as year-W and the number, for example 2026-W47."
            else -> "Type a date and time like 2026-11-20T14:30."
        }
        return question(prompt, "Pick a value", how, "OK", "Cancel", input = prompt.defaultValue.orEmpty()) { yes, text, _ ->
            if (yes && text.isNotBlank()) prompt.confirm(text.trim()) else prompt.dismiss()
        }
    }

    override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt): GeckoResult<PromptDelegate.PromptResponse>? =
        question(prompt, "Pick a colour", "Type a colour like #ff8800.", "OK", "Cancel", input = prompt.defaultValue.orEmpty()) { yes, text, _ ->
            if (yes && text.isNotBlank()) prompt.confirm(text.trim()) else prompt.dismiss()
        }

    /** The system file picker for <input type=file>. The engine copies what is chosen into its own folder. */
    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptDelegate.PromptResponse>? {
        val picker = browser.filePicker ?: return GeckoResult.fromValue(prompt.dismiss())
        val result = GeckoResult<PromptDelegate.PromptResponse>()
        val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
        picker(prompt.mimeTypes ?: emptyArray(), multiple) { uris ->
            runCatching {
                result.complete(
                    when {
                        uris.isEmpty() -> prompt.dismiss()
                        multiple -> prompt.confirm(app, uris.toTypedArray())
                        else -> prompt.confirm(app, uris[0])
                    },
                )
            }
        }
        return result
    }
}

/**
 * What a site asks to use: location, camera, microphone. Each is a question each time. Notifications, tracking exceptions, persistent
 * storage, third-party storage access and loud autoplay are refused without asking: Sheer has no push, and a quiet "no" is the private default.
 */
class PagePermissions(private val browser: Browser, private val tab: BTab) : PermissionDelegate {

    /** The engine needs an Android permission (camera, location...). Ask Android; the activity does the asking. */
    override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<out String>?, callback: PermissionDelegate.Callback) {
        val list = permissions?.toList().orEmpty()
        val askAndroid = browser.askAndroid
        if (list.isEmpty() || askAndroid == null) {
            callback.reject()
            return
        }
        askAndroid(list) { granted -> if (granted) callback.grant() else callback.reject() }
    }

    override fun onContentPermissionRequest(session: GeckoSession, perm: PermissionDelegate.ContentPermission): GeckoResult<Int>? {
        return when (perm.permission) {
            PermissionDelegate.PERMISSION_GEOLOCATION -> {
                val result = GeckoResult<Int>()
                val site = hostOf(perm.uri)
                PageAsks.show(
                    Ask("Location", "$site wants to know where you are.", "Allow", "Block") { yes, _, _ ->
                        result.complete(if (yes) PermissionDelegate.ContentPermission.VALUE_ALLOW else PermissionDelegate.ContentPermission.VALUE_DENY)
                    },
                )
                result
            }
            PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE, PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS ->
                GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_ALLOW)
            else -> GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)
        }
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out PermissionDelegate.MediaSource>?,
        audio: Array<out PermissionDelegate.MediaSource>?,
        callback: PermissionDelegate.MediaCallback,
    ) {
        val v = video?.firstOrNull()
        val a = audio?.firstOrNull()
        val what = when {
            v != null && a != null -> "camera and microphone"
            v != null -> "camera"
            a != null -> "microphone"
            else -> {
                callback.reject()
                return
            }
        }
        PageAsks.show(
            Ask("Use your $what?", "${hostOf(uri)} wants to use your $what.", "Allow", "Block") { yes, _, _ ->
                if (yes) callback.grant(v, a) else callback.reject()
            },
        )
    }
}
