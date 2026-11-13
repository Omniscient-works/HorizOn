package dev.horizon.ui.lyrics

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.horizon.media.NowPlaying
import dev.horizon.ui.artOutline
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Assumed tempo for the pulse. The real tempo isn't known without listening to the audio. */
private const val BPM = 118f

/** Flowing bands of light across the screen. */
private const val RIBBONS = 4

/** Points along each band; enough for a smooth curve on a wide screen. */
private const val RIBBON_STEPS = 56

/** Slow motes of light drifting upward. */
private const val MOTES = 42

/** 0..1 pulse that peaks on each beat and decays; flat while paused. */
internal fun beatPulse(seconds: Float, playing: Boolean, bpm: Float = BPM): Float {
    if (!playing) return 0f
    val phase = (seconds * bpm / 60f).mod(1f)
    return exp(-phase * 5f)
}

/** The accent turned around the colour wheel by [degrees], for a family of related colours. */
private fun Color.shiftHue(degrees: Float, lighten: Float = 0f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees).mod(360f)
    hsv[1] = (hsv[1] * (1f - lighten * 0.5f)).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] + (1f - hsv[2]) * lighten).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private class Mote(val x: Float, val speed: Float, val sway: Float, val phase: Float, val size: Float, val hue: Int)

/**
 * Shown when a song has no lyrics (and always in the offline build): an aurora. Bands of light in
 * colours drawn from the album's [accent] flow across the screen and swell on each beat, motes of
 * light drift upward, and the cover floats in the middle. Everything else stays black for OLED
 * screens. It does not listen to the audio, which would need the microphone permission
 * (Lyrics_plan.md, section 7); it follows the playback position instead, so it rests while paused.
 */
@Composable
fun Visualizer(nowPlaying: NowPlaying, accent: Color, modifier: Modifier = Modifier) {
    val track by rememberUpdatedState(nowPlaying)
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frame = it }
    }
    val palette = remember(accent) {
        listOf(accent, accent.shiftHue(-28f), accent.shiftHue(34f, 0.15f), accent.shiftHue(0f, 0.45f))
    }
    // A fresh scatter of motes each time the visualizer appears.
    val motes = remember {
        val r = Random(System.nanoTime())
        List(MOTES) { Mote(r.nextFloat(), 0.012f + r.nextFloat() * 0.03f, r.nextFloat() * 0.04f, r.nextFloat() * 6.28f, 0.8f + r.nextFloat() * 1.8f, r.nextInt(4)) }
    }
    // Each band gets its own speed, waviness and height.
    val seeds = remember { val r = Random(System.nanoTime() + 1); List(RIBBONS) { FloatArray(6) { r.nextFloat() } } }
    fun pulse(): Float = beatPulse(track.positionAt(SystemClock.elapsedRealtime()) / 1000f, track.isPlaying)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val coverSize = min(maxWidth, maxHeight) * 0.26f
        Canvas(Modifier.matchParentSize()) {
            if (frame < 0L) return@Canvas // Reading the frame clock makes this redraw every frame.
            val now = SystemClock.elapsedRealtime()
            // Song time drives the motion, so it rests with the music; a slow drift keeps it alive.
            val t = track.positionAt(now) / 1000f + now / 1000f * 0.08f
            val beat = pulse()
            val w = size.width
            val h = size.height
            val path = Path()
            val edge = Path()

            for (i in 0 until RIBBONS) {
                val s = seeds[i]
                val color = palette[i % palette.size]
                val baseY = h * (0.36f + 0.1f * i + (s[0] - 0.5f) * 0.08f)
                val amp = h * (0.06f + s[1] * 0.07f) * (1f + beat * 0.45f)
                val k1 = (1.2f + s[2] * 1.6f) * 2f * PI.toFloat() / w
                val k2 = (2.4f + s[3] * 2.2f) * 2f * PI.toFloat() / w
                val speed = 0.25f + s[4] * 0.35f
                val thick = h * (0.05f + s[5] * 0.06f) * (1f + beat * 0.3f)
                fun top(x: Float) = baseY + amp * (0.65f * sin(x * k1 + t * speed + s[0] * 6f) + 0.35f * sin(x * k2 - t * speed * 1.7f + i))
                fun depth(x: Float) = thick * (0.55f + 0.45f * sin(x * k1 * 0.7f - t * speed * 0.8f + s[3] * 6f))
                path.reset()
                edge.reset()
                for (step in 0..RIBBON_STEPS) {
                    val x = w * step / RIBBON_STEPS
                    val y = top(x)
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    if (step == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
                }
                for (step in RIBBON_STEPS downTo 0) {
                    val x = w * step / RIBBON_STEPS
                    path.lineTo(x, top(x) + depth(x))
                }
                path.close()
                val glow = 0.14f + 0.1f * beat
                drawPath(
                    path,
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = glow * 1.6f), color.copy(alpha = glow * 0.5f), Color.Transparent),
                        startY = baseY - amp,
                        endY = baseY + amp + thick,
                    ),
                )
                drawPath(edge, color.copy(alpha = 0.35f + 0.4f * beat), style = Stroke(width = 1.4f * density))
            }

            // Motes rise from the bottom and wrap around, brightening on the beat.
            for (m in motes) {
                val y = h * (1.05f - ((t * m.speed + m.phase) % 1.1f))
                val x = w * (m.x + m.sway * sin(t * 0.6f + m.phase))
                val twinkle = 0.35f + 0.35f * sin(t * 2.2f + m.phase * 3f) + 0.3f * beat
                drawCircle(palette[m.hue].copy(alpha = twinkle.coerceIn(0f, 1f) * 0.8f), m.size * density, Offset(x, y))
            }

            // A soft light behind the cover.
            val centre = Offset(w / 2, h / 2)
            val halo = coverSize.toPx() * (1.1f + 0.15f * beat)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f + 0.15f * beat), Color.Transparent), centre, halo), halo, centre)
        }
        Cover(
            nowPlaying.art,
            accent,
            Modifier
                .size(coverSize)
                .graphicsLayer {
                    if (frame < 0L) return@graphicsLayer
                    val now = SystemClock.elapsedRealtime()
                    val s = 1f + pulse() * 0.035f
                    scaleX = s
                    scaleY = s
                    // A gentle float, so the cover feels carried by the light.
                    translationY = sin(now / 1_400f) * 6f * density
                    rotationZ = sin(now / 2_300f) * 1.5f
                },
        )
    }
}

@Composable
private fun Cover(art: Bitmap?, accent: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape).artOutline(shape),
        )
    } else {
        Box(
            modifier
                .clip(shape)
                .background(Brush.linearGradient(listOf(accent, Color.Black)))
                .artOutline(shape),
        )
    }
}
