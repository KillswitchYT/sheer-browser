package com.killswitch.sheer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Choices that change how the screen is laid out, held as Compose state so a change in Settings shows at once. */
object UiPrefs {
    /** The address bar sits at the top instead of the bottom. */
    var addressTop by mutableStateOf(false)

    /** The page runs under the bottom glass bars, so the bars blur it. Off keeps the page clear of them. */
    var pageUnder by mutableStateOf(true)

    /** Frosted glass: the bars blur what is behind them and sit on a stronger tint, so their text and icons stay readable over any page.
     *  Off is the clear glass: no blur, almost no tint, the page bends at the rim. */
    var frosted by mutableStateOf(false)
}
