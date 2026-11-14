package dev.horizon.ui.lyrics

import android.graphics.Bitmap
import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.min
import androidx.palette.graphics.Palette
import dev.horizon.media.NowPlaying
import dev.horizon.ui.artOutline
import dev.horizon.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Assumed tempo for the pulse. The real tempo isn't known without listening to the audio. */
private const val BPM = 118f

/** Background stars, spread over three depths. */
private const val STARS = 190

/** Stars in the galaxy's spiral arms. */
private const val ARM_STARS = 820

/** Soft coloured nebula clouds along the disc. */
private const val NEBULAE = 7

/** How flat the galaxy's disc looks from here (1 = face on). */
private const val TILT = 0.5f

/** A shooting star crosses every this many seconds or so. */
private const val METEOR_EVERY_S = 9f

/** How long the whirl-and-bloom takes when the song changes. */
private const val SWAP_MS = 1_150f

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

/** Colour [c] made bright enough to read as a star on black, keeping its hue and saturation. */
private fun visible(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[2] = hsv[2].coerceAtLeast(0.62f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Whether two colours are close enough to count as one. */
private fun near(a: Color, b: Color): Boolean {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return dr * dr + dg * dg + db * db < 0.02f
}

/**
 * The cover's main colours, most common first, near-duplicates dropped: what the galaxy is made of.
 * Null until worked out (off the main thread), or when there's no cover.
 */
@Composable
private fun rememberCoverColours(art: Bitmap?): List<Color>? {
    var colours by remember { mutableStateOf<List<Color>?>(null) }
    LaunchedEffect(art) {
        colours = if (art == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                val distinct = ArrayList<Color>()
                Palette.from(art).maximumColorCount(16).generate().swatches
                    .sortedByDescending { it.population }
                    .forEach { sw ->
                        val c = visible(Color(sw.rgb))
                        if (distinct.none { near(it, c) }) distinct += c
                    }
                distinct.take(4).takeIf { it.isNotEmpty() }
            }
        }
    }
    return colours
}

/** Core to rim from the cover's colours: a pale core from the lightest, then the colours themselves. */
private fun paletteFromCover(cover: List<Color>): List<Color> {
    val base = cover.first()
    val lightest = cover.maxBy { it.red + it.green + it.blue }
    val rest = cover + listOf(base.shiftHue(-24f, 0.25f), base.shiftHue(34f, 0.5f), base.shiftHue(12f, 0.3f))
    return listOf(lightest.shiftHue(0f, 0.8f)) + rest.take(4)
}

private fun paletteOf(accent: Color) =
    listOf(accent.shiftHue(10f, 0.85f), accent.shiftHue(0f, 0.45f), accent, accent.shiftHue(-24f, 0.25f), accent.shiftHue(34f, 0.5f))

/** A background star: where it sits (0..1), how deep (0 far .. 1 near), its twinkle, and the rare bright one. */
private class Star(val x: Float, val y: Float, val depth: Float, val phase: Float, val warm: Boolean, val bright: Boolean)

/** A star in the galaxy: its distance from the core (0..1), angle, colour band, and whether it's loose fill. */
private class ArmStar(val r: Float, val angle: Float, val size: Float, val band: Int, val phase: Float, val loose: Boolean)

/** A soft colour cloud sitting on the disc at radius [r] and angle, carried round with the arms. */
private class Nebula(val r: Float, val angle: Float, val size: Float, val band: Int)

/**
 * Shown when a song has no lyrics (and always in the offline build): a small galaxy. Its spiral
 * arms, nebula clouds and a bright core, all in colours drawn from the album's [accent], turn
 * slowly around the cover, which sits at the heart like a planet with a glowing rim. Stars drift
 * past at three depths and twinkle on the beat, and now and then a shooting star crosses. When the
 * song changes the whole galaxy whirls up, collapses toward the core and blooms back out in the new
 * song's colours. The rest stays black for OLED screens. It does not listen to the audio, which
 * would need the microphone permission (Lyrics_plan.md, section 7); it follows the playback
 * position instead, so the galaxy all but stops while paused.
 */
@Composable
fun Visualizer(nowPlaying: NowPlaying, accent: Color, modifier: Modifier = Modifier) {
    val track by rememberUpdatedState(nowPlaying)
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frame = it }
    }
    // A song change whirls the old galaxy into the core and blooms the new one out. We blend from
    // the palette we're settled in, and when the change began, and read a 0..1 progress each frame.
    // The cover's own colours when we have them; the single accent until then (or with no cover).
    val cover = rememberCoverColours(nowPlaying.art)
    val newPalette = remember(accent, cover) { cover?.let(::paletteFromCover) ?: paletteOf(accent) }
    var fromPalette by remember { mutableStateOf(newPalette) }
    var swapAt by remember { mutableLongStateOf(-1L) }
    var firstSong by remember { mutableStateOf(true) }
    val artKey = nowPlaying.art?.let { "${it.width}x${it.height}@${it.generationId}" } ?: nowPlaying.title
    LaunchedEffect(artKey) {
        if (!firstSong) {
            // fromPalette still holds the previous song's colours; whirl, then settle into the new.
            swapAt = SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(SWAP_MS.toLong())
        }
        firstSong = false
        fromPalette = newPalette
    }

    // A fresh sky each time the visualizer appears.
    val seed = remember { System.nanoTime() }
    val stars = remember(seed) {
        val r = Random(seed)
        List(STARS) { Star(r.nextFloat(), r.nextFloat(), r.nextFloat().let { it * it }, r.nextFloat() * 6.28f, r.nextFloat() < 0.25f, r.nextFloat() < 0.06f) }
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
    val nebulae = remember(seed) {
        val r = Random(seed + 2)
        List(NEBULAE) {
            val rad = 0.25f + r.nextFloat() * 0.7f
            Nebula(rad, if (r.nextBoolean()) 0f else PI.toFloat() + (r.nextFloat() - 0.5f) * 1.2f, 0.4f + r.nextFloat() * 0.7f, (1 + r.nextInt(4)))
        }
    }
    fun pulse(): Float = beatPulse(track.positionAt(SystemClock.elapsedRealtime()) / 1000f, track.isPlaying)
    // 0 while settled; 0..1 during a song change.
    fun swap(now: Long): Float = if (swapAt <= 0L) 0f else ((now - swapAt) / SWAP_MS).coerceIn(0f, 1f)

    // Volumetric gas clouds on Android 13+; the soft blobs below stand in on older phones.
    val clouds = remember { GalaxyClouds.createOrNull() }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val coverSize = min(maxWidth, maxHeight) * 0.24f
        Canvas(Modifier.matchParentSize()) {
            if (frame < 0L) return@Canvas // Reading the frame clock makes this redraw every frame.
            val now = SystemClock.elapsedRealtime()
            // Song time drives the motion, so it rests with the music; a slow drift keeps it alive.
            val t = track.positionAt(now) / 1000f + now / 1000f * 0.03f
            val beat = pulse()
            val sw = swap(now)
            // The whirl: spin races up and eases out; the disc collapses toward the core at the
            // half-way point, where the colours cross over, then blooms back out.
            val whirl = whirlOf(sw)
            val extraSpin = whirl * 5.5f
            val discScale = 1f - whirl * 0.9f
            val pal = lerpPalette(fromPalette, newPalette, smoothstep(0.42f, 0.62f, sw))
            val centre = Offset(size.width / 2, size.height / 2)
            val cover = coverSize.toPx()

            drawStarfield(stars, t, beat)
            drawGalaxy(arms, nebulae, clouds, pal, centre, cover, t, beat, extraSpin, discScale, back = true)
            // The core's light, behind the planet, flaring at the crossover.
            val flare = 1f + whirl * 1.1f
            val core = cover * (1.5f + 0.25f * beat) * flare * discScale.coerceAtLeast(0.2f)
            drawCircle(Brush.radialGradient(listOf(pal[0].copy(alpha = (0.34f + 0.18f * beat + 0.4f * whirl).coerceAtMost(1f)), pal[2].copy(alpha = 0.12f), Color.Transparent), centre, core), core, centre)
            drawMeteor(seed, now, pal[0])
        }
        Planet(
            nowPlaying.art,
            accent,
            Modifier
                .size(coverSize)
                .graphicsLayer {
                    if (frame < 0L) return@graphicsLayer
                    val now = SystemClock.elapsedRealtime()
                    val sw = swap(now)
                    val whirl = whirlOf(sw)
                    // The planet shrinks into the core and spins with the galaxy, then grows back.
                    val s = (1f + pulse() * 0.03f) * (1f - whirl * 0.92f)
                    scaleX = s
                    scaleY = s
                    rotationZ = whirl * 220f
                    alpha = (1f - whirl * 0.4f)
                },
        )
        // The near half of the galaxy passes in front of the planet.
        Canvas(Modifier.matchParentSize()) {
            if (frame < 0L) return@Canvas
            val now = SystemClock.elapsedRealtime()
            val t = track.positionAt(now) / 1000f + now / 1000f * 0.03f
            val sw = swap(now)
            val whirl = whirlOf(sw)
            val pal = lerpPalette(fromPalette, newPalette, smoothstep(0.42f, 0.62f, sw))
            drawGalaxy(arms, nebulae, clouds, pal, Offset(size.width / 2, size.height / 2), coverSize.toPx(), t, pulse(), whirl * 5.5f, 1f - whirl * 0.9f, back = false)
            // The planet's atmosphere: a thin rim of light.
            val rim = coverSize.toPx() / 2f * (1f - whirl * 0.92f)
            drawCircle(pal[2].copy(alpha = 0.45f * (1f - whirl)), rim + 1.5f * density, Offset(size.width / 2, size.height / 2), style = Stroke(2f * density))
        }
    }
}

/**
 * How far into the whirl a song change is (0 settled .. 1 at the crossover) for progress [sw].
 * sin(PI) comes out a hair below zero in floats, and a negative number to a fractional power is NaN,
 * which once drew the galaxy at a NaN size and made it vanish after a change; so clamp first.
 */
private fun whirlOf(sw: Float): Float = sin(sw * PI.toFloat()).coerceAtLeast(0f).pow(0.6f)

/** Blends 0 below [from], 1 above [to], smoothly between. */
private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpPalette(a: List<Color>, b: List<Color>, f: Float): List<Color> =
    if (f <= 0f) a else if (f >= 1f) b else List(a.size) { lerp(a[it], b[it], f) }

/**
 * Stars at three depths drift slowly sideways (nearer ones faster) and twinkle, more on the beat.
 * A few bright ones get a soft cross-shaped glint.
 */
private fun DrawScope.drawStarfield(stars: List<Star>, t: Float, beat: Float) {
    for (s in stars) {
        val speed = 0.004f + 0.02f * s.depth
        val x = ((s.x - t * speed) % 1f + 1f) % 1f * size.width
        val y = s.y * size.height
        val twinkle = 0.55f + 0.45f * sin(t * (1.2f + s.depth * 2f) + s.phase)
        val alpha = ((0.15f + 0.6f * s.depth) * twinkle + 0.25f * beat * s.depth).coerceIn(0f, 1f)
        val color = if (s.warm) Color(0xFFFFE2B8) else Color(0xFFDCE6FF)
        val rad = (0.5f + 1.3f * s.depth) * density
        drawCircle(color.copy(alpha = alpha), rad, Offset(x, y))
        if (s.bright) {
            val g = rad * (3.5f + 2f * twinkle + 2f * beat)
            drawLine(color.copy(alpha = alpha * 0.5f), Offset(x - g, y), Offset(x + g, y), 0.8f * density)
            drawLine(color.copy(alpha = alpha * 0.5f), Offset(x, y - g), Offset(x, y + g), 0.8f * density)
        }
    }
}

/**
 * The spiral galaxy, seen tilted, centred on [centre]. Drawn in two passes: the far half ([back])
 * before the planet, the near half after it, so the arms wrap around it. [extraSpin] whirls it on a
 * song change and [discScale] collapses it toward the core.
 */
private fun DrawScope.drawGalaxy(
    arms: List<ArmStar>,
    nebulae: List<Nebula>,
    clouds: GalaxyClouds?,
    palette: List<Color>,
    centre: Offset,
    cover: Float,
    t: Float,
    beat: Float,
    extraSpin: Float,
    discScale: Float,
    back: Boolean,
) {
    val radius = minOf(size.width * 0.36f, size.height * 0.82f) * discScale
    val inner = cover * 0.55f * discScale.coerceAtLeast(0.25f)
    val spin = t * 0.09f + extraSpin
    // Gas clouds first, so the stars sit in front of them: volumetric where the phone can, else soft blobs.
    val volumetric = clouds != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        clouds.draw(this, centre, radius, inner, TILT, spin, t, beat, palette, front = !back)
    if (!volumetric) for (nb in nebulae) {
        val r = inner + (radius - inner) * nb.r
        val a = nb.angle + ln(1f + nb.r * 8f) * 2.9f + spin * (1.3f - 0.6f * nb.r)
        if ((sin(a) < 0f) != back) continue
        val p = centre + Offset(cos(a) * r, sin(a) * r * TILT)
        val blob = nb.size * cover * (0.9f + 0.2f * beat)
        drawCircle(Brush.radialGradient(listOf(palette[nb.band].copy(alpha = 0.07f + 0.04f * beat), Color.Transparent), p, blob), blob, p)
    }
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
        // Each star drifts slowly between its colour and the next, so the galaxy's colours move.
        val drift = 0.5f + 0.5f * sin(t * 0.08f + s.phase * 1.3f)
        val colour = lerp(palette[s.band], palette[(s.band + 1).coerceAtMost(palette.size - 1)], drift * 0.7f)
        drawCircle(colour.copy(alpha = alpha.coerceIn(0f, 1f)), s.size * density * (0.8f + 0.2f * depth) * (1f + 0.25f * beat), p)
    }
    // A faint glow along the disc. Drawn as a circle squashed by the tilt, so the gradient itself is
    // elliptical and fades to true black at the edge all round (a circular gradient in an oval left
    // a visible rim top and bottom). Kept faint: on an OLED screen every lit pixel counts.
    if (back) {
        val glow = radius * 0.9f
        scale(scaleX = 1f, scaleY = TILT, pivot = centre) {
            drawCircle(
                Brush.radialGradient(
                    0f to palette[2].copy(alpha = 0.07f + 0.04f * beat),
                    0.45f to palette[2].copy(alpha = 0.03f),
                    1f to Color.Transparent,
                    center = centre,
                    radius = glow,
                ),
                glow,
                centre,
            )
        }
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
    if (r.nextFloat() < 0.3f) return // Some periods pass without one.
    val start = Offset(size.width * (0.15f + r.nextFloat() * 0.7f), size.height * (r.nextFloat() * 0.35f))
    val angle = (0.35f + r.nextFloat() * 0.5f) * (if (r.nextBoolean()) 1f else -1f) + PI.toFloat() / 2f
    val dir = Offset(cos(angle), sin(angle))
    val travel = hypot(size.width, size.height) * 0.4f
    val head = start + dir * (travel * k)
    val tail = head - dir * (110f * density * (1f - k * 0.5f))
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
