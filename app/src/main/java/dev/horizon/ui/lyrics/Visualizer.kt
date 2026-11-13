package dev.horizon.ui.lyrics

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.min
import dev.horizon.media.NowPlaying
import dev.horizon.ui.artOutline
import dev.horizon.ui.theme.Paper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Assumed tempo for the pulse. The real tempo isn't known without listening to the audio. */
private const val BPM = 118f

/** Background stars, spread over three depths. */
private const val STARS = 170

/** Stars in the galaxy's spiral arms. */
private const val ARM_STARS = 720

/** How flat the galaxy's disc looks from here (1 = face on). */
private const val TILT = 0.5f

/** A shooting star crosses every this many seconds or so. */
private const val METEOR_EVERY_S = 11f

/** 0..1 pulse that peaks on each beat and decays; flat while paused. */
internal fun beatPulse(seconds: Float, playing: Boolean, bpm: Float = BPM): Float {
    if (!playing) return 0f
    val phase = (seconds * bpm / 60f).mod(1f)
    return exp(-phase * 5f)
}

/** The accent turned around the colour wheel by [degrees], and paled by [lighten] (0..1). */
private fun Color.shiftHue(degrees: Float, lighten: Float = 0f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees).mod(360f)
    hsv[1] = (hsv[1] * (1f - lighten * 0.7f)).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] + (1f - hsv[2]) * lighten).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** A background star: where it sits (0..1), how deep (0 far .. 1 near), and its twinkle. */
private class Star(val x: Float, val y: Float, val depth: Float, val phase: Float, val warm: Boolean)

/** A star in the galaxy: its distance from the core (0..1), angle, and colour band. */
private class ArmStar(val r: Float, val angle: Float, val size: Float, val band: Int, val phase: Float, val loose: Boolean)

/**
 * Shown when a song has no lyrics (and always in the offline build): a small galaxy. Its spiral
 * arms, in colours drawn from the album's [accent], turn slowly around the cover, which sits at the
 * core like a planet with a glowing rim. Stars drift past at three depths and twinkle on the beat,
 * and now and then a shooting star crosses. The rest stays black for OLED screens. It does not
 * listen to the audio, which would need the microphone permission (Lyrics_plan.md, section 7);
 * it follows the playback position instead, so the galaxy all but stops while paused.
 */
@Composable
fun Visualizer(nowPlaying: NowPlaying, accent: Color, modifier: Modifier = Modifier) {
    val track by rememberUpdatedState(nowPlaying)
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frame = it }
    }
    // Core to rim: pale and warm, the accent, then a cooler neighbour on the colour wheel.
    val palette = remember(accent) {
        listOf(accent.shiftHue(8f, 0.8f), accent.shiftHue(0f, 0.4f), accent, accent.shiftHue(-22f, 0.25f), accent.shiftHue(30f, 0.45f))
    }
    // A fresh sky each time the visualizer appears.
    val seed = remember { System.nanoTime() }
    val stars = remember(seed) {
        val r = Random(seed)
        List(STARS) { Star(r.nextFloat(), r.nextFloat(), r.nextFloat().let { it * it }, r.nextFloat() * 6.28f, r.nextFloat() < 0.25f) }
    }
    val arms = remember(seed) {
        val r = Random(seed + 1)
        List(ARM_STARS) {
            // Denser toward the core. Most stars follow one of two arms, a little scattered;
            // the rest lie loose across the disc, dimmer, to fill it out.
            val rad = r.nextFloat().pow(1.5f) * 0.97f + 0.03f
            val arm = if (r.nextBoolean()) 0f else PI.toFloat()
            val loose = r.nextFloat() < 0.3f
            val scatter = if (loose) r.nextFloat() * 6.28f else (r.nextFloat() - 0.5f) * r.nextFloat() * (1.1f - 0.4f * rad)
            val band = ((rad * 3.4f) + r.nextFloat() * 1.2f).toInt().coerceIn(0, 4)
            val size = if (loose) 0.4f + r.nextFloat() * 0.8f else 0.5f + r.nextFloat() * 1.2f
            ArmStar(rad, arm + scatter, size, band, r.nextFloat() * 6.28f, loose)
        }
    }
    fun pulse(): Float = beatPulse(track.positionAt(SystemClock.elapsedRealtime()) / 1000f, track.isPlaying)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val coverSize = min(maxWidth, maxHeight) * 0.24f
        Canvas(Modifier.matchParentSize()) {
            if (frame < 0L) return@Canvas // Reading the frame clock makes this redraw every frame.
            val now = SystemClock.elapsedRealtime()
            // Song time drives the motion, so it rests with the music; a slow drift keeps it alive.
            val t = track.positionAt(now) / 1000f + now / 1000f * 0.03f
            val beat = pulse()
            val w = size.width
            val h = size.height
            val centre = Offset(w / 2, h / 2)
            val cover = coverSize.toPx()

            drawStarfield(stars, t, beat)
            drawGalaxy(arms, palette, centre, cover, t, beat, back = true)
            // The core's light, behind the planet.
            val core = cover * (1.5f + 0.25f * beat)
            drawCircle(Brush.radialGradient(listOf(palette[0].copy(alpha = 0.35f + 0.2f * beat), accent.copy(alpha = 0.12f), Color.Transparent), centre, core), core, centre)
            drawMeteor(seed, now, palette[0])
        }
        Planet(
            nowPlaying.art,
            accent,
            Modifier
                .size(coverSize)
                .graphicsLayer {
                    if (frame < 0L) return@graphicsLayer
                    val s = 1f + pulse() * 0.03f
                    scaleX = s
                    scaleY = s
                },
        )
        // The near half of the galaxy passes in front of the planet.
        Canvas(Modifier.matchParentSize()) {
            if (frame < 0L) return@Canvas
            val now = SystemClock.elapsedRealtime()
            val t = track.positionAt(now) / 1000f + now / 1000f * 0.03f
            drawGalaxy(arms, palette, Offset(size.width / 2, size.height / 2), coverSize.toPx(), t, pulse(), back = false)
            // The planet's atmosphere: a thin rim of light.
            val rim = coverSize.toPx() / 2f
            drawCircle(accent.copy(alpha = 0.45f), rim + 1.5f * density, Offset(size.width / 2, size.height / 2), style = Stroke(2f * density))
        }
    }
}

/** Stars at three depths drift slowly sideways (nearer ones faster) and twinkle, more on the beat. */
private fun DrawScope.drawStarfield(stars: List<Star>, t: Float, beat: Float) {
    for (s in stars) {
        val speed = 0.004f + 0.02f * s.depth
        val x = ((s.x - t * speed) % 1f + 1f) % 1f * size.width
        val y = s.y * size.height
        val twinkle = 0.55f + 0.45f * sin(t * (1.2f + s.depth * 2f) + s.phase)
        val alpha = (0.15f + 0.6f * s.depth) * twinkle + 0.25f * beat * s.depth
        val color = if (s.warm) Color(0xFFFFE2B8) else Color(0xFFDCE6FF)
        drawCircle(color.copy(alpha = alpha.coerceIn(0f, 1f)), (0.5f + 1.3f * s.depth) * density, Offset(x, y))
    }
}

/**
 * The spiral galaxy, seen tilted, centred on [centre]. Drawn in two passes: the far half ([back])
 * before the planet, the near half after it, so the arms wrap around it.
 */
private fun DrawScope.drawGalaxy(arms: List<ArmStar>, palette: List<Color>, centre: Offset, cover: Float, t: Float, beat: Float, back: Boolean) {
    val radius = minOf(size.width * 0.36f, size.height * 0.82f)
    val inner = cover * 0.55f
    val spin = t * 0.09f
    for (s in arms) {
        // A logarithmic spiral: farther stars trail further behind as the galaxy turns.
        val r = inner + (radius - inner) * s.r
        val a = s.angle + ln(1f + s.r * 8f) * 2.9f + spin * (1.3f - 0.6f * s.r)
        val sinA = sin(a)
        if ((sinA < 0f) != back) continue
        val p = centre + Offset(cos(a) * r, sinA * r * TILT)
        // The far side is dimmer and a touch smaller, which sells the depth.
        val depth = if (back) 0.65f else 1f
        val twinkle = 0.6f + 0.4f * sin(t * 1.7f + s.phase)
        val alpha = (0.85f - 0.45f * s.r) * twinkle * depth * (if (s.loose) 0.45f else 1f) + 0.3f * beat * (1f - s.r)
        val color = palette[s.band]
        drawCircle(color.copy(alpha = alpha.coerceIn(0f, 1f)), s.size * density * (0.8f + 0.2f * depth) * (1f + 0.25f * beat), p)
    }
    // A faint dusty glow along the disc.
    if (back) {
        val glow = radius * 0.9f
        drawOval(
            Brush.radialGradient(listOf(palette[2].copy(alpha = 0.10f + 0.06f * beat), Color.Transparent), centre, glow),
            topLeft = Offset(centre.x - glow, centre.y - glow * TILT),
            size = androidx.compose.ui.geometry.Size(glow * 2, glow * 2 * TILT),
        )
    }
}

/**
 * Now and then a shooting star: a short bright streak with a fading tail, on a path picked from
 * [seed] and the time, so it's different every crossing.
 */
private fun DrawScope.drawMeteor(seed: Long, now: Long, color: Color) {
    val period = METEOR_EVERY_S * 1000f
    val n = (now / period).toLong()
    val k = (now % period.toLong()) / 900f
    if (k >= 1f) return
    val r = Random(seed xor n)
    if (r.nextFloat() < 0.35f) return // Some periods pass without one.
    val start = Offset(size.width * (0.15f + r.nextFloat() * 0.7f), size.height * (r.nextFloat() * 0.35f))
    val angle = (0.35f + r.nextFloat() * 0.5f) * (if (r.nextBoolean()) 1f else -1f) + PI.toFloat() / 2f
    val dir = Offset(cos(angle), sin(angle))
    val travel = size.minDimension * 0.55f
    val head = start + dir * (travel * k)
    val tail = head - dir * (90f * density * (1f - k * 0.5f))
    val fade = sin(k * PI.toFloat())
    drawLine(Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = 0.9f * fade)), tail, head), tail, head, 1.6f * density, StrokeCap.Round)
    drawCircle(lerp(color, Paper, 0.5f).copy(alpha = fade), 1.8f * density, head)
}

@Composable
private fun Planet(art: Bitmap?, accent: Color, modifier: Modifier) {
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Box(modifier, contentAlignment = Alignment.Center) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape).artOutline(CircleShape))
            // Night side: the planet is lit from the upper left.
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
            )
        }
    } else {
        Box(
            modifier
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, Color.Black)))
                .artOutline(CircleShape),
        )
    }
}
