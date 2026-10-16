package com.suvelwebverse.tickwell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * A live analog watch showing the phone's time. The seconds hand steps once a
 * second with a small spring overshoot, like a quartz movement; the hour and
 * minute hands update with it. Drawn, not an image, so it has no brand text.
 */
@Composable
fun LiveWatch(
    dial: Color,
    rim: Color,
    marks: Color,
    hands: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val start = remember { now - now % 1000 }
    val seconds = remember { Animatable(secondOf(start).toFloat()) }

    // One step per second, with a spring for the quartz-style overshoot. The target
    // counts on past 60 (from real elapsed time, so it can't drift after a pause),
    // so the hand never sweeps backwards at the top of the minute.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
            val target = secondOf(start) + (now - start) / 1000
            seconds.animateTo(
                target.toFloat(),
                spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessHigh),
            )
        }
    }

    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val minute = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
    val hour = cal.get(Calendar.HOUR) + minute / 60f

    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center

        // Case and dial.
        drawCircle(Brush.linearGradient(listOf(rim, rim.copy(alpha = 0.55f), rim)), r)
        drawCircle(
            Brush.radialGradient(listOf(dial.copy(alpha = 0.92f), dial), c, r),
            r * 0.92f,
        )

        // Minute track and hour batons.
        for (i in 0 until 60) {
            val major = i % 5 == 0
            rotate(i * 6f, c) {
                drawLine(
                    color = if (major) marks else marks.copy(alpha = 0.45f),
                    start = Offset(c.x, c.y - r * 0.86f),
                    end = Offset(c.x, c.y - r * (if (major) 0.72f else 0.81f)),
                    strokeWidth = if (major) r * 0.035f else r * 0.012f,
                    cap = StrokeCap.Round,
                )
            }
        }

        hand(hour * 30f, r * 0.48f, r * 0.055f, hands)
        hand(minute * 6f, r * 0.72f, r * 0.04f, hands)
        hand(seconds.value * 6f, r * 0.80f, r * 0.014f, accent, tail = r * 0.18f)
        drawCircle(accent, r * 0.045f, c)
        drawCircle(dial, r * 0.018f, c)
        drawCircle(rim.copy(alpha = 0.6f), r * 0.92f, c, style = Stroke(r * 0.01f))
    }
}

private fun DrawScope.hand(degrees: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
    rotate(degrees, center) {
        drawLine(
            color = color,
            start = Offset(center.x, center.y + tail),
            end = Offset(center.x, center.y - length),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

private fun secondOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.SECOND)
