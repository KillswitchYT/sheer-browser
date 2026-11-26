package com.killswitch.sheer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.killswitch.sheer.Extensions
import com.killswitch.sheer.glass.LiquidPanel
import com.kyant.backdrop.Backdrop
import org.mozilla.geckoview.GeckoView

/** An extension's toolbar popup (the uBlock Origin panel, the Bitwarden vault) in a sheet over the app. Tapping outside or going back closes it. */
@Composable
fun ExtensionPopupSheet(backdrop: Backdrop, popup: Extensions.Popup) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    BackHandler { Extensions.closePopup() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }.background(Color.Black.copy(alpha = .45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Extensions.closePopup() },
        )
        LiquidPanel(
            backdrop,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp).navigationBarsPadding()
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 80.dp.toPx() }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            corner = 32.dp,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        ) {
            BasicText(popup.title, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp), style = ts(17f, weight = FontWeight.ExtraBold))
            // A TextureView: the page inside shows up inside the glass, a SurfaceView would not.
            AndroidView(
                factory = { ctx ->
                    GeckoView(ctx).also {
                        it.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                        it.setSession(popup.session)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(520.dp).clip(RoundedCornerShape(22.dp)),
            )
        }
    }
}
