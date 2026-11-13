package dev.horizon.ui.lyrics

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import dev.horizon.lyrics.Constellation
import dev.horizon.lyrics.ConstellationEgg
import dev.horizon.lyrics.LYRICS_BREAK_MS
import dev.horizon.lyrics.LYRICS_HOLD_MS
import dev.horizon.lyrics.TimedWord
import dev.horizon.lyrics.countAt
import dev.horizon.media.NowPlaying
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** How long the line to a new word takes to draw. */
private const val EDGE_DRAW_MS = 420f

/** A new word fades in over this long. */
private const val APPEAR_MS = 280f

/** The ring that marks a new word's arrival. */
private const val PULSE_MS = 900f

/** Finished lines fade out over this long after their line ends, shrinking slightly as they go. */
private const val FADE_MS = 16_000f
private const val SHRINK_MS = 2_500f

/** How many random versions of a line's shape are tried before keeping the clearest. */
private const val LAYOUT_TRIES = 8

/** A jump of more than this many words (a seek forward) rebuilds from the current line. */
private const val MAX_CATCH_UP = 12

/** A line followed by at least this much silence is a held ending and gets a finale. */
private const val HELD_MS = 2_000L

/** How long a finale's glow and shockwave take. */
private const val FINALE_MS = 1_400f

/** Over this long before the outro takes over, the last line's shape drifts apart and fades. */
private const val DISSOLVE_MS = 1_600f

/** When more than this share of lines repeat, the song is loops and nothing counts as a chorus. */
private const val MAX_CHORUS_SHARE = 0.6f

/** Constellation: how close the view stays while it builds, and how the reveal plays out. */
private const val SKY_ZOOM = 2.3f
private const val REVEAL_MS = 1_800f
private const val SKY_HOLD_MS = 6_000f
private const val SKY_FADE_MS = 3_000f

/**
 * Time-synced lyrics in the Echo style (Lyrics_plan.md, section 4): each lyric line becomes its own
 * small shape, a different random path every time; a line that comes back (a chorus) redraws its shape somewhere new, with a thread back;
 * a word sung earlier in another line gets a dotted thread back to it; the current word grows bolder
 * the longer it is held. Very rarely ([ConstellationEgg]) the words build a real constellation,
 * which only shows itself once complete. Follows [nowPlaying]'s position, so pause, seek and skip
 * just work. [engine] holds the song's shapes, so they survive the visualizer taking over in breaks.
 */
@Composable
internal fun LyricsWeb(engine: EchoEngine, nowPlaying: NowPlaying, accent: Color, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val track by rememberUpdatedState(nowPlaying)
    val measurer = rememberTextMeasurer(cacheSize = 64)
    // Only the draw phase reads the frame clock, so each frame redraws without recomposing.
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(engine) {
        while (true) withFrameMillis { frame = it }
    }
    Canvas(modifier) {
        if (frame < 0L) return@Canvas // Reading the frame clock makes this redraw every frame.
        val now = SystemClock.elapsedRealtime()
        engine.update(track.positionAt(now), now, size.width, size.height, density, reduceMotion)
        engine.draw(this, now, accent, measurer, reduceMotion)
    }
}

/** What the timings say about a line: a repeated line is a chorus, a line followed by silence is held. */
private class LineInfo(val lastWordMs: Long, val nextStartMs: Long?, val held: Boolean, val chorus: Boolean, val leaves: Boolean)

private class Node(val word: TimedWord, val idx: Int, val at: Offset, val born: Long) {
    val layouts = HashMap<Int, TextLayoutResult>(4)
}

private class Shape(val line: Int, val key: String, val pts: List<Offset>, val centre: Offset, val retrace: Boolean, val offset: Int) {
    val nodes = ArrayList<Node>()
    var alpha = 1f
    var shrink = 1f
}

private class Thread(val a: Node, val aShape: Shape, val b: Node, val bShape: Shape, val born: Long)

private class Sky(val c: Constellation, val from: Int, val pos: List<Offset>, val centre: Offset, val bottom: Float) {
    val lit = ArrayList<Pair<Int, Node>>()
    val caption = ArrayList<Node>()
    var doneAt: Long? = null
    var alpha = 1f
    var focus: Offset? = null
    var focusAtDone: Offset? = null
}

/** The song's Echo shapes and state, kept for as long as the lyrics are on screen. */
internal class EchoEngine(private val words: List<TimedWord>) {
    /** Fresh every time, so a song never lays out the same way twice. */
    private val random = Random(System.nanoTime())
    private val wordInLine = IntArray(words.size)
    private val lineSize = HashMap<Int, Int>()
    private val lineKey = HashMap<Int, String>()
    private val info: Map<Int, LineInfo>

    /** Every line's shape, by its words, so a returning line can be recognised by its outline. */
    private val layouts = HashMap<String, List<Offset>>()
    private val shapes = ArrayList<Shape>()
    private val threads = ArrayList<Thread>()
    private var spawned = 0
    private var lastIdx = -1
    private var cam = Offset.Zero
    private var position = 0L
    private var width = 0f
    private var height = 0f
    private var density = 1f

    /** The rare constellation, rolled once for the song. */
    private var skyPending: Constellation? = ConstellationEgg.roll()
    private var sky: Sky? = null

    init {
        words.forEachIndexed { i, w ->
            val j = lineSize[w.line] ?: 0
            wordInLine[i] = j
            lineSize[w.line] = j + 1
        }
        words.groupBy { it.line }.forEach { (line, ws) -> lineKey[line] = ws.joinToString(" ") { norm(it.text) } }
        val lines = lineKey.keys.sorted()
        val counts = lineKey.values.groupingBy { it }.eachCount()
        val repeated = lineKey.filterValues { counts.getValue(it) > 1 }.keys
        val choruses = if (repeated.size <= lines.size * MAX_CHORUS_SHARE) repeated else emptySet()
        val byLine = words.groupBy { it.line }
        info = lines.mapIndexed { i, line ->
            val last = byLine.getValue(line).last().timeMs
            val next = lines.getOrNull(i + 1)?.let { byLine.getValue(it).first().timeMs }
            line to LineInfo(
                lastWordMs = last,
                nextStartMs = next,
                held = next == null || next - last >= HELD_MS,
                chorus = line in choruses,
                leaves = next == null || next - last >= LYRICS_BREAK_MS,
            )
        }.toMap()
    }

    fun update(positionMs: Long, now: Long, width: Float, height: Float, density: Float, reduceMotion: Boolean) {
        if (this.width != 0f && (abs(this.width - width) > 1f || abs(this.height - height) > 1f)) reset()
        this.width = width
        this.height = height
        this.density = density
        position = positionMs

        val target = words.countAt(positionMs)
        if (target < spawned || target - spawned > MAX_CATCH_UP) {
            // A seek: start again from the current line. A constellation that wasn't finished waits for later.
            val unfinished = sky?.takeIf { it.doneAt == null }?.c
            reset()
            skyPending = unfinished ?: skyPending
            spawned = if (target == 0) 0 else words.indexOfFirst { it.line == words[target - 1].line }
        }
        while (spawned < target) spawn(spawned++, now)

        sky?.let { s ->
            val done = s.doneAt
            s.alpha = if (done == null) 1f else (1f - (positionMs - done - REVEAL_MS - SKY_HOLD_MS) / SKY_FADE_MS).coerceIn(0f, 1f)
            if (s.alpha <= 0f) sky = null
        }
        val skyActive = (sky?.alpha ?: 0f) > 0.3f
        val latest = shapes.lastOrNull()
        val latestIsCurrent = latest?.nodes?.any { it.idx == lastIdx } == true
        for (s in shapes) {
            if (s === latest && latestIsCurrent) {
                s.alpha = 1f
                s.shrink = 1f
                continue
            }
            val since = max(0L, positionMs - lineEnd(s.line)).toFloat()
            s.alpha = (0.55f - since / FADE_MS).coerceIn(0f, 0.55f)
            s.shrink = 1f - 0.12f * (since / SHRINK_MS).coerceIn(0f, 1f)
            // While a line returns, or a constellation builds, everything else steps back.
            if (skyActive) s.alpha = min(s.alpha, 0.12f) else if (latest?.retrace == true && latestIsCurrent) s.alpha = min(s.alpha, 0.2f)
        }
        shapes.removeAll { it.alpha <= 0f }
        threads.removeAll { it.aShape !in shapes || it.bShape !in shapes }

        // The view drifts gently toward the word being sung; a constellation holds it still.
        val cur = latest?.nodes?.lastOrNull()?.takeIf { latestIsCurrent }
        cam = when {
            reduceMotion -> Offset.Zero
            skyActive -> cam * 0.97f
            cur != null -> cam + ((Offset(width / 2, height * 0.55f) - cur.at) * 0.18f - cam) * 0.03f
            else -> cam
        }
    }

    private fun reset() {
        layouts.clear()
        shapes.clear()
        threads.clear()
        sky = null
        lastIdx = -1
        cam = Offset.Zero
    }

    /** When a line is over: the next line's first word, or a few seconds after its own last word. */
    private fun lineEnd(line: Int): Long = info[line]?.let { it.nextStartMs ?: (it.lastWordMs + 3_000) } ?: 0L

    private fun spawn(idx: Int, now: Long) {
        val w = words[idx]
        val j = wordInLine[idx]
        lastIdx = idx
        val pending = skyPending
        if (pending != null && j == 0 && words.size - idx >= pending.starCount) {
            startSky(pending, idx)
            skyPending = null
        }
        sky?.let { s ->
            if (idx < s.from + s.c.starCount) {
                val star = idx - s.from
                s.lit += star to Node(w, idx, s.pos[star], now)
                if (s.lit.size == s.c.starCount) s.doneAt = position
                return
            }
            // While the finished figure holds with its name, the lyrics carry on as a caption beneath it.
            if (s.doneAt != null) {
                if (s.caption.firstOrNull()?.word?.line != w.line) s.caption.clear()
                s.caption += Node(w, idx, Offset.Zero, now)
                return
            }
        }
        // Words left over in the line that finished a constellation make a small shape of their own.
        val tailFrom = sky?.let { s -> if (words[s.from + s.c.starCount - 1].line == w.line) wordInLine[s.from + s.c.starCount] else 0 } ?: 0
        var shape = shapes.lastOrNull()
        var earlier: Shape? = null
        if (shape == null || shape.line != w.line) {
            val key = if (tailFrom > 0) "tail:${w.line}" else lineKey.getValue(w.line)
            val template = layouts[key]
            val retrace = template != null
            // A returning line keeps its outline but lands somewhere new.
            val pts = if (template != null) moveShape(template) else layoutLine(lineSize.getValue(w.line) - tailFrom).also { layouts[key] = it }
            val centre = pts.fold(Offset.Zero) { acc, p -> acc + p / pts.size.toFloat() }
            earlier = if (retrace) shapes.lastOrNull { it.key == key } else null
            shape = Shape(w.line, key, pts, centre, retrace, tailFrom)
            shapes += shape
        }
        val at = shape.pts[(j - shape.offset).coerceIn(0, shape.pts.size - 1)]
        val node = Node(w, idx, at, now)
        shape.nodes += node
        // A returning line: a thread back to where it was last sung, if that copy is still showing.
        earlier?.nodes?.firstOrNull()?.let { threads += Thread(it, earlier, node, shape, now) }
        // Echo thread: the same word, sung earlier in a different line that is still showing.
        val word = norm(w.text)
        if (!shape.retrace && word.length >= 3) {
            for (other in shapes) {
                if (other === shape) continue
                val twin = other.nodes.firstOrNull { norm(it.word.text) == word } ?: continue
                threads += Thread(twin, other, node, shape, now)
                break
            }
        }
    }

    private fun startSky(c: Constellation, from: Int) {
        val scale = min(width * 0.66f / c.width, height * 0.58f / c.height)
        val centre = Offset(width / 2, height * 0.53f)
        val x0 = centre.x - c.width * scale / 2
        val y0 = centre.y - c.height * scale / 2
        val pos = (0 until c.starCount).map { Offset(x0 + c.x(it) * scale, y0 + c.y(it) * scale) }
        sky = Sky(c, from, pos, centre, y0 + c.height * scale)
    }

    /**
     * Lays a line out as its own small shape, starting from a spot away from every shape drawn so far
     * (including ones that may return). Each line picks its own kind of path at random (a drifting
     * walk, an arc, a wave, an opening spiral, a loose scatter, now and then a zigzag) and a few
     * versions are tried, keeping the one that stays clearest of the other words.
     */
    private fun layoutLine(count: Int): List<Offset> {
        val left = width * 0.09f
        val right = width * 0.83f
        val top = height * 0.27f
        val bottom = height * 0.84f
        val skyPts = sky?.pos.orEmpty()
        val others = shapes.flatMap { it.pts } + skyPts
        val centres = shapes.map { it.centre } + listOfNotNull(sky?.centre)
        fun randomSpot() = Offset(left + random.nextFloat() * (right - left), top + random.nextFloat() * (bottom - top))
        fun roomAt(c: Offset) = centres.minOfOrNull { hypot(it.x - c.x, (it.y - c.y) * 1.3f) } ?: 1e6f
        var far = randomSpot()
        repeat(30) {
            val c = randomSpot()
            if (roomAt(c) > roomAt(far)) far = c
        }
        var best: List<Offset> = listOf(far)
        var bestScore = -1f
        repeat(LAYOUT_TRIES) { attempt ->
            // Mostly from the roomiest spot, sometimes from somewhere nearby, for variety.
            val origin = if (attempt % 3 == 0) far else Offset(
                (far.x + (random.nextFloat() - 0.5f) * width * 0.2f).coerceIn(left, right),
                (far.y + (random.nextFloat() - 0.5f) * height * 0.2f).coerceIn(top, bottom),
            )
            val pts = walk(origin, count, left, right, top, bottom)
            var score = 1e6f
            for ((i, p) in pts.withIndex()) {
                for (o in others) score = min(score, hypot(o.x - p.x, (o.y - p.y) * 1.4f))
                // Words of the same line shouldn't crowd each other either.
                for (j in 0 until i - 1) score = min(score, hypot(pts[j].x - p.x, pts[j].y - p.y) * 1.2f)
            }
            if (score > bestScore) {
                bestScore = score
                best = pts
            }
        }
        return best
    }

    /** One random path of [count] points from [origin], turning back from the edges it meets. */
    private fun walk(origin: Offset, count: Int, left: Float, right: Float, top: Float, bottom: Float): List<Offset> {
        val pts = ArrayList<Offset>(count)
        pts += origin
        if (count < 2) return pts
        val tau = 2f * PI.toFloat()
        // Set off roughly toward the open middle, at a random slant.
        var heading = atan2((top + bottom) / 2 - origin.y, (left + right) / 2 - origin.x) + (random.nextFloat() - 0.5f) * 2.4f
        val style = random.nextInt(100)
        val step = (46f + random.nextFloat() * 34f) * density
        val turn = (0.2f + random.nextFloat() * 0.35f) * (if (random.nextBoolean()) 1f else -1f)
        val waveAmp = 0.35f + random.nextFloat() * 0.6f
        val waveFreq = 0.8f + random.nextFloat() * 1.1f
        val phase = random.nextFloat() * tau
        var zig = if (random.nextBoolean()) 1f else -1f
        // Scatter: points around an oval, visited in order of angle, so it reads as a loose ring.
        val ringR = step * (0.55f + 0.18f * count)
        val ringStart = random.nextFloat() * tau
        val ringSquash = 0.55f + random.nextFloat() * 0.45f
        for (i in 1 until count) {
            var length = step * (0.75f + random.nextFloat() * 0.5f)
            var p: Offset
            if (style >= 92) {
                val a = ringStart + tau * i / count + (random.nextFloat() - 0.5f) * 0.5f
                val r = ringR * (0.7f + random.nextFloat() * 0.5f)
                p = origin + Offset(kotlin.math.cos(a) * r, sin(a) * r * ringSquash) - Offset(kotlin.math.cos(ringStart) * ringR, sin(ringStart) * ringR * ringSquash)
            } else {
                val dir = when {
                    style < 26 -> { heading += (random.nextFloat() - 0.5f) * 0.8f; heading } // drifting walk
                    style < 46 -> { heading += turn; heading } // arc
                    style < 68 -> { heading += (random.nextFloat() - 0.5f) * 0.2f; heading + waveAmp * sin(i * waveFreq + phase) } // wave
                    style < 84 -> { heading += turn * 2f / (1f + i * 0.35f); length *= 0.7f + i * 0.08f; heading } // opening spiral
                    else -> { zig = -zig; heading + zig * (0.45f + random.nextFloat() * 0.6f) } // zigzag, now and then
                }
                p = pts.last() + Offset(kotlin.math.cos(dir), sin(dir)) * length
            }
            if (p.x < left || p.x > right) heading = PI.toFloat() - heading
            if (p.y < top || p.y > bottom) heading = -heading
            pts += Offset(p.x.coerceIn(left, right), p.y.coerceIn(top, bottom))
        }
        return pts
    }

    /** Moves a remembered shape to a fresh spot, as far as it can from what is showing and from where it was. */
    private fun moveShape(template: List<Offset>): List<Offset> {
        val left = width * 0.09f
        val right = width * 0.83f
        val top = height * 0.27f
        val bottom = height * 0.84f
        val minX = template.minOf { it.x }
        val maxX = template.maxOf { it.x }
        val minY = template.minOf { it.y }
        val maxY = template.maxOf { it.y }
        val home = template.fold(Offset.Zero) { a, p -> a + p / template.size.toFloat() }
        val centres = shapes.map { it.centre } + listOfNotNull(sky?.centre) + home
        fun range(lo: Float, hi: Float, a: Float, b: Float) = if (b - a > hi - lo) Pair((lo + hi - a - b) / 2, (lo + hi - a - b) / 2) else Pair(lo - a, hi - b)
        val (dx0, dx1) = range(left, right, minX, maxX)
        val (dy0, dy1) = range(top, bottom, minY, maxY)
        var shift = Offset.Zero
        var best = -1f
        repeat(30) {
            val d = Offset(dx0 + random.nextFloat() * (dx1 - dx0), dy0 + random.nextFloat() * (dy1 - dy0))
            val c = home + d
            val score = centres.minOf { hypot(it.x - c.x, (it.y - c.y) * 1.3f) }
            if (score > best) {
                best = score
                shift = d
            }
        }
        return template.map { it + shift }
    }

    fun draw(scope: DrawScope, now: Long, accent: Color, measurer: TextMeasurer, reduceMotion: Boolean) {
        with(scope) {
            drawSky(now, accent, measurer, reduceMotion)
            translate(cam.x, cam.y) { drawShapes(now, accent, measurer, reduceMotion) }
        }
    }

    // ---- Lines, words and threads ----------------------------------------------------------

    private fun DrawScope.drawShapes(now: Long, accent: Color, measurer: TextMeasurer, reduceMotion: Boolean) {
        val latest = shapes.lastOrNull() ?: return
        val cur = latest.nodes.firstOrNull { it.idx == lastIdx }
        val line = info[latest.line]
        val chorus = line?.chorus == true

        // Finale: once a held line's last word lands, the line glows up and settles (0..1).
        val finale = if (cur != null && line != null && line.held && position >= line.lastWordMs) {
            ((position - line.lastWordMs) / FINALE_MS).coerceIn(0f, 1f)
        } else {
            -1f
        }
        val swell = if (finale >= 0f) sin(finale * PI.toFloat()) else 0f
        // Before a break or the end, the shape drifts apart and fades just ahead of the visualizer.
        val dissolve = if (line != null && line.leaves) {
            ((position - line.lastWordMs - (LYRICS_HOLD_MS - DISSOLVE_MS)) / DISSOLVE_MS).coerceIn(0f, 1f)
        } else {
            0f
        }
        val fade = 1f - dissolve
        val spread = if (reduceMotion) 0f else dissolve * dissolve * 60f * density

        fun at(s: Shape, n: Node): Offset {
            val p = s.centre + (n.at - s.centre) * s.shrink
            if (spread == 0f || s !== latest) return p
            val away = p - s.centre
            val d = away.getDistance().coerceAtLeast(1f)
            return p + away / d * spread
        }

        // Echo threads: dotted arcs back to where a word was first sung.
        for (t in threads) {
            val a = at(t.aShape, t.a)
            val b = at(t.bShape, t.b)
            val k = ease((now - t.born) / 900f)
            val alpha = (if (t.bShape === latest) 0.6f else min(t.aShape.alpha, t.bShape.alpha) * 0.55f) * fade
            val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2 - hypot(b.x - a.x, b.y - a.y) * 0.25f)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(a.x, a.y)
                for (step in 1..24) {
                    val u = step / 24f * k
                    val q = a * ((1 - u) * (1 - u)) + mid * (2 * (1 - u) * u) + b * (u * u)
                    lineTo(q.x, q.y)
                }
            }
            drawPath(
                path,
                accent.copy(alpha = alpha),
                style = Stroke(width = density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * density, 6 * density))),
            )
        }

        // A returning line shows its remembered shape at once, faint and dotted, and the words trace it.
        if (latest.retrace && cur != null) {
            val k = ease((now - (latest.nodes.firstOrNull()?.born ?: now)) / 600f) * fade
            val path = androidx.compose.ui.graphics.Path().apply {
                latest.pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            drawPath(path, Paper.copy(alpha = 0.3f * k), style = Stroke(width = density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(density, 5 * density))))
            for (p in latest.pts) drawCircle(Paper.copy(alpha = 0.35f * k), 2f * density, p)
        }

        // Each line's shape.
        for (s in shapes) {
            val isLatest = s === latest && cur != null
            for (k in 1 until s.nodes.size) {
                val a = at(s, s.nodes[k - 1])
                val b = at(s, s.nodes[k])
                val grow = ease((now - s.nodes[k].born) / EDGE_DRAW_MS)
                val end = a + (b - a) * grow
                if (isLatest && (s.retrace || swell > 0f)) {
                    // A returning line or a finale glows.
                    drawLine(accent.copy(alpha = (0.18f + 0.22f * swell) * fade), a, end, 6f * density, StrokeCap.Round)
                }
                drawLine(
                    color = if (isLatest) accent.copy(alpha = 0.85f * fade) else Paper.copy(alpha = 0.32f * s.alpha),
                    start = a,
                    end = end,
                    strokeWidth = (if (isLatest) (if (chorus) 2f else 1.5f) else 1f) * density,
                    cap = StrokeCap.Round,
                )
                if (isLatest && grow < 1f) spark(end, accent)
            }
        }

        // The shockwave: a ring that leaves the last word of a held line.
        if (cur != null && finale in 0f..1f && !reduceMotion) {
            val eased = 1f - (1f - finale) * (1f - finale)
            val ring = 0.5f * (1f - finale) * fade
            if (ring > 0f) {
                drawCircle(
                    color = accent.copy(alpha = ring),
                    radius = (26f + (if (chorus) 190f else 140f) * eased) * density,
                    center = at(latest, cur),
                    style = Stroke(width = (2.5f * (1f - finale) + 0.5f) * density),
                )
            }
        }

        // Words and their points.
        for (s in shapes) {
            val isLatest = s === latest && cur != null
            for (n in s.nodes) {
                val p = at(s, n)
                val isCur = n === cur
                val appear = ease((now - n.born) / APPEAR_MS)
                val alpha = (if (isLatest) 1f else s.alpha) * fade
                if (isCur) {
                    if (!reduceMotion) arrivalRing(p, n.born, now, accent, fade)
                    val r = (if (chorus) 30f else 22f) * (1f + 0.5f * swell) * density
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f * fade), Color.Transparent), p, r), r, p)
                }
                drawCircle(
                    color = if (isCur) accent.copy(alpha = appear * fade) else Paper.copy(alpha = if (isLatest) 0.9f * fade else 0.55f * alpha),
                    radius = (if (isCur) 3.6f else if (isLatest) 2.6f else 2f) * density,
                    center = p,
                )
                // An older word that a thread just reached back to lights up for a moment.
                val echoed = threads.firstOrNull { it.a === n && now - it.born < 2_200 }
                val (size, weight, color) = when {
                    isCur -> Triple(if (chorus) 38 else 32, heldWeight(n), Paper.copy(alpha = appear * fade))
                    isLatest -> Triple(if (chorus) 20 else 17, 600, Paper.copy(alpha = (0.85f + 0.15f * swell) * fade))
                    echoed != null -> Triple(15, 600, accent.copy(alpha = (0.15f + 0.8f * (1f - (now - echoed.born) / 2_200f)) * fade))
                    else -> Triple(13, 500, Muted.copy(alpha = min(1f, alpha * 1.6f)))
                }
                label(measurer, n, p, size, weight, color, if (isCur) 14f else 9f, isCur, accent)
            }
        }
    }

    // ---- The rare constellation ---------------------------------------------------------------

    /**
     * While it builds, the view stays close on the newest word, so it reads as ordinary lyrics and
     * nothing gives it away. When the last star lights, the view pulls back to show the whole figure,
     * which then lights up: real brightness, glowing lines, and its name in English and Hindi.
     */
    private fun DrawScope.drawSky(now: Long, accent: Color, measurer: TextMeasurer, reduceMotion: Boolean) {
        val s = sky ?: return
        val a = s.alpha
        val done = s.doneAt
        val rv = if (done != null) ease((position - done) / REVEAL_MS) else 0f
        val last = s.lit.lastOrNull()?.second
        val curLine = last?.word?.line
        if (done == null && last != null) {
            val t = last.at
            s.focus = s.focus?.let { it + (t - it) * 0.06f } ?: t
            s.focusAtDone = s.focus
        }
        val zoomIn = if (reduceMotion) 1f else SKY_ZOOM
        val from = s.focusAtDone ?: s.centre
        val focus = if (done != null) from + (s.centre - from) * rv else (s.focus ?: s.centre)
        val zoom = zoomIn + (1f - zoomIn) * rv
        fun view(p: Offset) = s.centre + (p - focus) * zoom
        val starRadius = { k: Int -> max(1.6f, 4.4f - 0.75f * s.c.magnitudes[k]) * density }
        val pulse = 0.5f + 0.5f * sin(now / 420f)
        val litAt = HashMap<Int, Node>().apply { s.lit.forEach { (k, n) -> put(k, n) } }

        // The figure's real lines, each drawn once both of its stars are lit.
        for (e in 0 until s.c.edges.size / 2) {
            val ni = litAt[s.c.edges[e * 2]] ?: continue
            val nj = litAt[s.c.edges[e * 2 + 1]] ?: continue
            val (older, newer) = if (ni.born <= nj.born) ni to nj else nj to ni
            val p0 = view(older.at)
            val p1 = view(newer.at)
            val grow = ease((now - newer.born) / EDGE_DRAW_MS)
            val end = p0 + (p1 - p0) * grow
            val inLine = done == null && newer.word.line == curLine
            if (rv > 0f) drawLine(accent.copy(alpha = (0.1f + 0.12f * pulse) * a * rv), p0, end, 6f * density, StrokeCap.Round)
            drawLine(
                color = when {
                    done != null -> accent.copy(alpha = a * (0.32f + 0.53f * rv))
                    inLine -> accent.copy(alpha = 0.85f)
                    else -> Paper.copy(alpha = 0.32f)
                },
                start = p0,
                end = end,
                strokeWidth = (if (done != null) 1f + 0.4f * rv else if (inLine) 1.5f else 1f) * density,
                cap = StrokeCap.Round,
            )
            if (inLine && grow < 1f) spark(end, accent)
        }

        // Stars and their words; the newest word is placed first and overlapping older labels are left out.
        val placed = ArrayList<FloatArray>()
        for ((k, n) in s.lit.sortedByDescending { it.second.born }) {
            val p = view(n.at)
            val isCur = n === last && done == null
            val sameLine = done == null && n.word.line == curLine
            val rBuild = (if (isCur) 3.6f else if (sameLine) 2.6f else 2f) * density
            val r = rBuild + (starRadius(k) - rBuild) * rv
            if (rv > 0f) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f * rv * a), Color.Transparent), p, r * 4), r * 4, p)
            if (isCur) {
                if (!reduceMotion) arrivalRing(p, n.born, now, accent, 1f)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent), p, 22f * density), 22f * density, p)
            }
            drawCircle(
                if (isCur) accent.copy(alpha = ease((now - n.born) / APPEAR_MS)) else Paper.copy(alpha = a * if (sameLine) 0.9f else 0.55f + 0.45f * rv),
                r,
                p,
            )
            val (size, weight, color) = when {
                isCur -> Triple(32, heldWeight(n), Paper.copy(alpha = ease((now - n.born) / APPEAR_MS)))
                sameLine -> Triple(17, 600, Paper.copy(alpha = 0.9f))
                else -> Triple(if (done != null) 12 else 13, 500, Muted.copy(alpha = a * if (done != null) 0.6f + 0.3f * (1f - rv) else 0.9f))
            }
            label(measurer, n, p, size, weight, color, r / density + if (isCur) 11f else 7f, isCur, accent, placed)
        }

        // Revealed: the name arrives as the view settles, with the lyrics carrying on beneath it.
        val nameIn = ((rv - 0.65f) / 0.35f).coerceIn(0f, 1f)
        if (nameIn > 0f) {
            val title = measurer.measure(
                "${s.c.name.uppercase()}  ·  ${s.c.hindi}",
                TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
                density = Density(density, fontScale),
            )
            val y = min(size.height - 52f * density, s.bottom + 26f * density)
            drawText(title, color = accent, topLeft = Offset(s.centre.x - title.size.width / 2f, y - title.size.height / 2f), alpha = a * nameIn)
            if (s.caption.isNotEmpty()) {
                val text = androidx.compose.ui.text.buildAnnotatedString {
                    s.caption.forEachIndexed { i, c ->
                        if (i > 0) append(' ')
                        val style = if (c.idx == lastIdx) androidx.compose.ui.text.SpanStyle(color = accent) else androidx.compose.ui.text.SpanStyle(color = Paper.copy(alpha = 0.75f))
                        pushStyle(style)
                        append(c.word.text)
                        pop()
                    }
                }
                val line = measurer.measure(text, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium), density = Density(density, fontScale))
                drawText(line, topLeft = Offset(s.centre.x - line.size.width / 2f, y + 12f * density), alpha = a)
            }
        }
    }

    // ---- Shared drawing ------------------------------------------------------------------------

    /** Light when the word starts, bolder the longer it is held (in steps of 100). */
    private fun heldWeight(n: Node): Int {
        val next = words.getOrNull(n.idx + 1)?.takeIf { it.line == n.word.line }?.timeMs
        val held = ((position - n.word.timeMs).toFloat() / max(350f, ((next ?: (n.word.timeMs + 600)) - n.word.timeMs).toFloat()))
        return (300 + 500 * ease(held)).toInt() / 100 * 100
    }

    private fun DrawScope.arrivalRing(p: Offset, born: Long, now: Long, accent: Color, fade: Float) {
        val k = (now - born) / PULSE_MS
        if (k >= 1f) return
        drawCircle(accent.copy(alpha = 0.6f * (1f - k) * fade), (4f + ease(k) * 34f) * density, p, style = Stroke(1.4f * density))
    }

    /** A spark running along a line to the newest word. */
    private fun DrawScope.spark(at: Offset, accent: Color) {
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.9f), Color.Transparent), at, 9f * density), 9f * density, at)
    }

    /**
     * Draws [n]'s word beside [p], on whichever side fits. With [placed], a label that would overlap
     * one already drawn is left out (the current word never is).
     */
    private fun DrawScope.label(
        measurer: TextMeasurer,
        n: Node,
        p: Offset,
        size: Int,
        weight: Int,
        color: Color,
        gapDp: Float,
        isCurrent: Boolean,
        accent: Color,
        placed: MutableList<FloatArray>? = null,
    ) {
        val layout = n.layouts.getOrPut(size * 1000 + weight) {
            measurer.measure(n.word.text, TextStyle(fontSize = size.sp, fontWeight = FontWeight(weight)), density = Density(density, fontScale))
        }
        val gap = gapDp * density
        val w = layout.size.width
        val h = layout.size.height
        var right = p.x + cam.x + gap + w < this.size.width
        fun box(side: Boolean) = if (side) floatArrayOf(p.x + gap, p.y - h / 2f, p.x + gap + w, p.y + h / 2f) else floatArrayOf(p.x - gap - w, p.y - h / 2f, p.x - gap, p.y + h / 2f)
        fun hits(b: FloatArray) = placed?.any { b[0] < it[2] && b[2] > it[0] && b[1] < it[3] && b[3] > it[1] } == true
        if (!isCurrent && hits(box(right))) right = !right
        if (!isCurrent && hits(box(right))) return
        placed?.add(box(right))
        val topLeft = Offset(if (right) p.x + gap else p.x - gap - w, p.y - h / 2f)
        if (isCurrent) {
            // A soft glow behind the word being sung.
            drawText(layout, color = accent.copy(alpha = 0.35f * color.alpha), topLeft = topLeft + Offset(0f, 1f * density))
        }
        drawText(layout, color = color, topLeft = topLeft)
    }
}

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() || it == '\'' }

private fun ease(t: Float) = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)

private fun abs(v: Float) = kotlin.math.abs(v)
