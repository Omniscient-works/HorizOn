package dev.horizon.ui

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One pass of the shower; it repeats with fresh paths for as long as the minute lasts. */
private const val SHOWER_CYCLE_MS = 12_000L

/** Shooting stars per pass. */
private const val METEORS = 30

/** "make a wish" fades in after this, stays, then fades out over the last part. */
private const val WISH_IN_MS = 1_200f
private const val WISH_HOLD_MS = 7_000f
private const val WISH_OUT_MS = 2_000f

private val Starlight = listOf(Color(0xFFFFFFFF), Color(0xFFFFE6B0), Color(0xFFD6E4FF))

/** The 11:11 minute: when it is, and a way for debug builds to see it without waiting. */
object WishEgg {
    /** Debug builds set this (elapsedRealtime) to show the shower until then. */
    @Volatile
    var forceUntil: Long = 0L

    /** Whether the clock reads 11:11: in the morning or evening on a 12-hour clock, 11:11 on a 24-hour one. */
    fun isWishMinute(hour: Int, minute: Int, is24Hour: Boolean): Boolean =
        minute == 11 && (if (is24Hour) hour == 11 else hour % 12 == 11)

    fun forced(): Boolean = SystemClock.elapsedRealtime() < forceUntil
}

/**
 * While [visible], a gentle meteor shower falls across the whole screen, all from one point up to
 * the right like a real shower, and "make a wish" fades in for a few seconds. With reduce motion on,
 * only the words appear. It doesn't take touches, so everything under it works as usual.
 */
@Composable
fun WishShower(visible: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(1_500)), exit = fadeOut(tween(2_500)), modifier = modifier) {
        val start = remember { SystemClock.elapsedRealtime() }
        val seed = remember { System.nanoTime() }
        var frame by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) withFrameMillis { frame = it }
        }
        Box(Modifier.fillMaxSize()) {
            if (!reduceMotion) {
                Canvas(Modifier.fillMaxSize()) {
                    if (frame < 0L) return@Canvas // Reading the frame clock makes this redraw every frame.
                    drawShower(seed, SystemClock.elapsedRealtime() - start)
                }
            }
            Text(
                "make a wish",
                color = Color(0xFFFFE6B0),
                fontSize = 15.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.3.em,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .graphicsLayer {
                        if (frame < 0L) return@graphicsLayer
                        val e = (SystemClock.elapsedRealtime() - start).toFloat()
                        alpha = when {
                            e < WISH_IN_MS -> 0f
                            e < WISH_IN_MS * 2 -> (e - WISH_IN_MS) / WISH_IN_MS
                            e < WISH_IN_MS * 2 + WISH_HOLD_MS -> 1f
                            else -> (1f - (e - WISH_IN_MS * 2 - WISH_HOLD_MS) / WISH_OUT_MS).coerceIn(0f, 1f)
                        } * 0.85f
                    },
            )
        }
    }
}

/** The shooting stars of this moment of the shower, [elapsed] ms after it began. */
private fun DrawScope.drawShower(seed: Long, elapsed: Long) {
    val pass = elapsed / SHOWER_CYCLE_MS
    val inPass = (elapsed % SHOWER_CYCLE_MS).toFloat()
    // This pass and the one before, so stars that started near the end of the last pass finish.
    for (p in longArrayOf(pass - 1, pass)) {
        if (p < 0) continue
        val r = Random(seed xor (p * 7_919L))
        val passStart = (p - pass) * SHOWER_CYCLE_MS + 0f
        // All from one radiant up and to the right, falling down-left, a little spread.
        val heading = (PI * 0.78).toFloat()
        repeat(METEORS) {
            val begins = passStart + r.nextFloat() * SHOWER_CYCLE_MS
            val lasts = 900f + r.nextFloat() * 700f
            val sx = size.width * (0.15f + r.nextFloat() * 1.0f)
            val sy = size.height * (-0.1f + r.nextFloat() * 0.55f)
            val a = heading + (r.nextFloat() - 0.5f) * 0.35f
            val travel = size.minDimension * (0.35f + r.nextFloat() * 0.35f)
            val tail = (70f + r.nextFloat() * 90f) * density
            val colour = Starlight[r.nextInt(Starlight.size)]
            val k = (inPass - begins) / lasts
            if (k !in 0f..1f) return@repeat
            val dir = Offset(cos(a), sin(a))
            val head = Offset(sx, sy) + dir * (travel * k)
            val back = head - dir * (tail * (0.4f + 0.6f * sin(k * PI.toFloat()).coerceAtLeast(0f)))
            val fade = sin(k * PI.toFloat()).coerceAtLeast(0f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, colour.copy(alpha = 0.85f * fade)), back, head), back, head, 1.4f * density, StrokeCap.Round)
            // A small four-point glint at the head.
            val g = (3f + 4f * fade) * density
            drawLine(colour.copy(alpha = 0.6f * fade), head - Offset(g, 0f), head + Offset(g, 0f), 0.8f * density)
            drawLine(colour.copy(alpha = 0.6f * fade), head - Offset(0f, g), head + Offset(0f, g), 0.8f * density)
            drawCircle(colour.copy(alpha = fade), 1.6f * density, head)
        }
    }
}
