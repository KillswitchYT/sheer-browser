// Adapted from the glass dialog in Kyant0/AndroidLiquidGlass (Apache License 2.0), catalog/destinations/DialogContent.kt: the same shape,
// colorControls, blur, lens, highlight and surface recipe. Only the two colours follow Aura's theme instead of the system's, and the
// corner and padding are parameters. See third_party/AndroidLiquidGlass-LICENSE.
package com.killswitch.sheer.glass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.killswitch.sheer.ui.Aura
import com.killswitch.sheer.ui.UiPrefs
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/** A panel of real library glass, for a group of rows. */
@Composable
fun LiquidPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    corner: Dp = 32.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isLightTheme = !Aura.isDark
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
        else Color(0xFF121212).copy(0.4f)
    Column(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(corner) },
                effects = {
                    colorControls(
                        brightness = if (isLightTheme) 0.2f else 0f,
                        saturation = 1.5f
                    )
                    blur(if (isLightTheme) 16f.dp.toPx() else 8f.dp.toPx())
                    lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Plain },
                onDrawSurface = { drawRect(containerColor) }
            )
            .padding(contentPadding),
        content = content,
    )
}

/** A glass pill for typing in, with the same effects as the library's buttons. */
@Composable
fun LiquidField(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val isLightTheme = !Aura.isDark
    val frosted = UiPrefs.frosted
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    if (frosted) {
                        // Frosted: the page behind is blurred hard and only the rim bends, so the text on top stays easy to read.
                        blur(14f.dp.toPx())
                        lens(8f.dp.toPx(), 14f.dp.toPx())
                    } else {
                        // Clear glass: no blur at all, so the page behind stays sharp, and a strong lens that bends it at the rim.
                        lens(16f.dp.toPx(), 32f.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    }
                },
                highlight = { Highlight.Default },
                innerShadow = { if (frosted) null else InnerShadow(radius = 6f.dp, alpha = 0.25f) },
                onDrawSurface = {
                    drawRect(
                        if (frosted) (if (isLightTheme) Color.White.copy(0.68f) else Color(0xFF0E1522).copy(0.64f))
                        else (if (isLightTheme) Color.Black.copy(0.05f) else Color.Black.copy(0.17f))
                    )
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
