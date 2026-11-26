package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.sin

private data class TouchRipple(
    val x: Float,
    val y: Float,
    var radius: Float,
    var alpha: Float
)

/**
 * Lightweight 60fps native Compose preview for the default Aurora Pulse wallpaper
 * shown in the Android phone mockup before the user uploads a custom file.
 */
@Composable
fun BuiltinShowcasePreview(
    wallpaperId: String,
    touchEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var timeSec by remember { mutableFloatStateOf(0f) }
    val ripples = remember { mutableStateListOf<TouchRipple>() }

    LaunchedEffect(wallpaperId) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    timeSec += dt
                    for (i in ripples.lastIndex downTo 0) {
                        val r = ripples[i]
                        r.radius += dt * 210f
                        r.alpha -= dt * 0.9f
                        if (r.alpha <= 0.02f) {
                            ripples.removeAt(i)
                        }
                    }
                }
                lastNanos = frameNanos
            }
        }
    }

    val gestureMod = if (touchEnabled) {
        Modifier.pointerInput(wallpaperId) {
            detectTapGestures { offset ->
                ripples.add(TouchRipple(offset.x, offset.y, 12f, 0.9f))
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050811))
            .then(gestureMod)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF040712), Color(0xFF0A1428), Color(0xFF0F1B33))
            )
        )

        for (i in 0 until 45) {
            val sx = ((sin(i * 91.7f) * 0.5f + 0.5f) * w)
            val sy = ((cos(i * 53.3f) * 0.5f + 0.5f) * h * 0.75f)
            val twinkle = 0.3f + 0.6f * (0.5f + 0.5f * sin(timeSec * (1.2f + (i % 4) * 0.4f) + i))
            drawCircle(
                color = Color(0xFFE2E8F0).copy(alpha = twinkle),
                radius = 1.6f + (i % 3) * 0.8f,
                center = Offset(sx, sy)
            )
        }

        val ribbons = listOf(
            Triple(0.36f, 44f, Color(0xFF22D3EE)),
            Triple(0.49f, 52f, Color(0xFF34D399)),
            Triple(0.62f, 38f, Color(0xFFFF7A59))
        )
        ribbons.forEachIndexed { idx, (baseYFrac, amp, color) ->
            val path = Path()
            val baseY = h * baseYFrac
            path.moveTo(0f, h)
            var x = 0f
            val step = (w / 24f).coerceAtLeast(12f)
            val dir = if (idx % 2 == 0) 1f else -0.8f
            while (x <= w + step) {
                val y = baseY +
                    sin(x * 0.006f + timeSec * 1.1f * dir + idx) * amp +
                    cos(x * 0.011f - timeSec * 0.7f) * (amp * 0.4f)
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(w, h)
            path.close()
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.36f),
                        color.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    startY = baseY - amp,
                    endY = baseY + h * 0.35f
                )
            )
        }

        ripples.forEach { rp ->
            drawCircle(
                color = Color(0xFF22D3EE).copy(alpha = rp.alpha.coerceIn(0f, 1f)),
                radius = rp.radius,
                center = Offset(rp.x, rp.y),
                style = Stroke(width = 3f)
            )
        }
    }
}
