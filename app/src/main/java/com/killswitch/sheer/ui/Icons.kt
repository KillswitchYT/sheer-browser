package com.killswitch.sheer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class Ic { World, Setup, Plus, Chevron, Back, Reload, Tabs, Lock, Trash, Alert, Search, Close, Check, Copy, Shield, Info, Moon, Sun, Private, Clock, More }

/** Tiny stroke icons drawn on a canvas, so the app needs no icon library. */
@Composable
fun Icon(kind: Ic, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val sw = s * .09f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val c = center
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), sw, StrokeCap.Round)
        when (kind) {
            Ic.World -> {
                drawCircle(color, s * .38f, c, style = st)
                drawOval(color, Offset(s * .31f, s * .12f), Size(s * .38f, s * .76f), style = st)
                line(.12f, .5f, .88f, .5f)
            }
            Ic.Setup -> {
                for (i in 0..2) line(.14f, .25f + .25f * i, .86f, .25f + .25f * i)
                val xs = listOf(.34f, .66f, .46f)
                for (i in 0..2) drawCircle(color, s * .075f, Offset(xs[i] * s, (.25f + .25f * i) * s))
            }
            Ic.Plus -> { line(.5f, .2f, .5f, .8f); line(.2f, .5f, .8f, .5f) }
            Ic.Chevron -> { line(.38f, .2f, .66f, .5f); line(.66f, .5f, .38f, .8f) }
            Ic.Back -> { line(.62f, .2f, .34f, .5f); line(.34f, .5f, .62f, .8f) }
            Ic.Reload -> {
                drawArc(
                    color = color, startAngle = -30f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(s * .2f, s * .2f), size = Size(s * .6f, s * .6f), style = st,
                )
                line(.5f, .2f, .38f, .08f); line(.5f, .2f, .38f, .32f)
            }
            Ic.Tabs -> {
                drawRoundRect(color, Offset(s * .16f, s * .2f), Size(s * .68f, s * .6f), CornerRadius(s * .1f), style = st)
                line(.16f, .4f, .84f, .4f)
            }
            Ic.Lock -> {
                drawRoundRect(color, Offset(s * .24f, s * .46f), Size(s * .52f, s * .38f), CornerRadius(s * .07f), style = st)
                drawArc(
                    color = color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(s * .33f, s * .2f), size = Size(s * .34f, s * .5f), style = st,
                )
            }
            Ic.Trash -> {
                drawRoundRect(color, Offset(s * .28f, s * .34f), Size(s * .44f, s * .50f), CornerRadius(s * .06f), style = st)
                line(.18f, .26f, .82f, .26f); line(.42f, .15f, .58f, .15f)
            }
            Ic.Alert -> {
                val p = Path().apply { moveTo(s * .5f, s * .14f); lineTo(s * .9f, s * .84f); lineTo(s * .1f, s * .84f); close() }
                drawPath(p, color, style = st)
                line(.5f, .42f, .5f, .62f); drawCircle(color, s * .04f, Offset(s * .5f, s * .74f))
            }
            Ic.Close -> { line(.25f, .25f, .75f, .75f); line(.75f, .25f, .25f, .75f) }
            Ic.Check -> { line(.2f, .52f, .42f, .74f); line(.42f, .74f, .8f, .28f) }
            Ic.Copy -> {
                drawRoundRect(color, Offset(s * .18f, s * .14f), Size(s * .46f, s * .52f), CornerRadius(s * .07f), style = st)
                drawRoundRect(color, Offset(s * .36f, s * .34f), Size(s * .46f, s * .52f), CornerRadius(s * .07f), style = st)
            }
            Ic.Search -> { drawCircle(color, s * .26f, Offset(s * .44f, s * .44f), style = st); line(.64f, .64f, .86f, .86f) }
            Ic.Shield -> {
                val p = Path().apply {
                    moveTo(s * .5f, s * .12f); lineTo(s * .84f, s * .26f); lineTo(s * .84f, s * .52f)
                    lineTo(s * .5f, s * .88f); lineTo(s * .16f, s * .52f); lineTo(s * .16f, s * .26f); close()
                }
                drawPath(p, color, style = st)
                line(.35f, .5f, .46f, .61f); line(.46f, .61f, .66f, .39f)
            }
            Ic.Info -> {
                drawCircle(color, s * .38f, c, style = st)
                line(.5f, .46f, .5f, .68f); drawCircle(color, s * .035f, Offset(s * .5f, s * .31f))
            }
            Ic.Moon -> {
                val p = Path().apply {
                    moveTo(s * .62f, s * .14f)
                    cubicTo(s * .30f, s * .18f, s * .16f, s * .46f, s * .28f, s * .68f)
                    cubicTo(s * .40f, s * .88f, s * .68f, s * .90f, s * .86f, s * .70f)
                    cubicTo(s * .58f, s * .72f, s * .46f, s * .42f, s * .62f, s * .14f)
                    close()
                }
                drawPath(p, color, style = st)
            }
            Ic.Private -> {
                drawRoundRect(color, Offset(s * .12f, s * .30f), Size(s * .76f, s * .38f), CornerRadius(s * .17f), style = st)
                drawCircle(color, s * .06f, Offset(s * .35f, s * .49f))
                drawCircle(color, s * .06f, Offset(s * .65f, s * .49f))
                line(.30f, .80f, .70f, .80f)
            }
            Ic.Clock -> {
                drawCircle(color, s * .38f, c, style = st)
                line(.5f, .28f, .5f, .5f); line(.5f, .5f, .66f, .6f)
            }
            Ic.More -> for (x in listOf(.22f, .5f, .78f)) drawCircle(color, s * .075f, Offset(x * s, .5f * s))
            Ic.Sun -> {
                drawCircle(color, s * .17f, c, style = st)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    val ca = Math.cos(a).toFloat(); val sa = Math.sin(a).toFloat()
                    line(.5f + .28f * ca, .5f + .28f * sa, .5f + .4f * ca, .5f + .4f * sa)
                }
            }
        }
    }
}
