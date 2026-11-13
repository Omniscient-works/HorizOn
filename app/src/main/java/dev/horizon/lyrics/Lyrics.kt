package dev.horizon.lyrics

/** One word of a lyric line. [timeMs] is set only by "enhanced" LRC files that time each word. */
data class LyricWord(val text: String, val timeMs: Long? = null)

/** A line of lyrics starting at [timeMs]. An empty line is a break (an instrumental gap). */
data class LyricLine(val timeMs: Long, val words: List<LyricWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
}

/** Time-synced lyrics, lines in time order. */
data class Lyrics(val lines: List<LyricLine>)

/** A word with the time it is sung, after spreading untimed words across their line. */
data class TimedWord(val line: Int, val text: String, val timeMs: Long)

/**
 * Parses LRC text: `[mm:ss.xx]Line`, several time tags on one line, `[offset:±ms]`, and enhanced
 * `<mm:ss.xx>word` timing. Metadata tags (`[ar:…]`, `[ti:…]`) and untimed lines are ignored.
 */
object LrcParser {
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("\\s+")

    fun parse(lrc: String): Lyrics {
        // A positive offset means the lyrics should show earlier.
        val offset = OFFSET_TAG.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            var rest = raw.trim()
            val times = mutableListOf<Long>()
            while (true) {
                val m = TIME_TAG.matchAt(rest, 0) ?: break
                times += millis(m) - offset
                rest = rest.substring(m.range.last + 1)
            }
            if (times.isEmpty()) continue
            val words = words(rest.trim(), offset)
            times.forEach { lines += LyricLine(it.coerceAtLeast(0), words) }
        }
        return Lyrics(lines.sortedBy { it.timeMs })
    }

    private fun words(text: String, offset: Long): List<LyricWord> {
        if (!WORD_TAG.containsMatchIn(text)) {
            return text.split(SPACES).filter { it.isNotEmpty() }.map { LyricWord(it) }
        }
        val out = mutableListOf<LyricWord>()
        var time: Long? = null
        var last = 0
        fun flush(chunk: String) {
            chunk.split(SPACES).filter { it.isNotEmpty() }.forEachIndexed { i, w ->
                // Only the first word after a tag carries its time.
                out += LyricWord(w, if (i == 0) time else null)
            }
        }
        for (m in WORD_TAG.findAll(text)) {
            flush(text.substring(last, m.range.first))
            time = (millis(m) - offset).coerceAtLeast(0)
            last = m.range.last + 1
        }
        flush(text.substring(last))
        return out
    }

    private fun millis(m: MatchResult): Long {
        val (min, sec, frac) = m.destructured
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
    }
}

/** A line is sung over at most this long; the rest of a longer gap is a pause. */
private const val MAX_LINE_MS = 6_000L

/** Words finish at most this share of the way to the next line, leaving a beat before it starts. */
private const val LINE_FILL = 0.9

/** A word's weight is its syllables plus this, for the small gap a singer leaves between words. */
private const val WORD_GAP = 0.35

/** Singing pace, in milliseconds per unit of weight: the song's own, kept within these bounds. */
private const val MIN_PACE_MS = 110.0
private const val MAX_PACE_MS = 420.0
private const val DEFAULT_PACE_MS = 240.0

/** A line may run this much slower than the song's pace before it's treated as a pause after it. */
private const val PACE_SLACK = 1.15

/**
 * Roughly how many syllables [word] is sung over: vowel groups for Latin script (a silent final e
 * dropped, "y" counted as a vowel only after a consonant), one per character for Chinese, Japanese and Korean, about one per two letters otherwise.
 */
internal fun syllables(word: String): Int {
    val letters = word.lowercase().filter { it.isLetter() }
    if (letters.isEmpty()) return 1
    if (letters.all { it in 'a'..'z' }) {
        var groups = 0
        var inVowel = false
        for ((k, ch) in letters.withIndex()) {
            // "y" is a vowel ("my", "rhythm") except at the start or straight after a vowel ("you", "say").
            val vowel = ch in "aeiou" || (ch == 'y' && k > 0 && !inVowel)
            if (vowel && !inVowel) groups++
            inVowel = vowel
        }
        if (groups > 1 && letters.length > 2 && letters.endsWith('e') && !letters.endsWith("le")) groups--
        return groups.coerceAtLeast(1)
    }
    if (letters.all { Character.isIdeographic(it.code) || it in '\u3040'..'\u30ff' || it in '\uac00'..'\ud7af' }) return letters.length
    return ((letters.length + 1) / 2).coerceIn(1, 6)
}

private fun weight(word: LyricWord) = syllables(word.text) + WORD_GAP

/**
 * The song's singing pace (ms per unit of word weight), from how tightly its lines are packed.
 * A line followed by a pause looks slow, so a low percentile is taken: the densely sung lines show
 * the real pace.
 */
private fun Lyrics.pace(): Double {
    val samples = lines.mapIndexedNotNull { i, line ->
        val next = lines.getOrNull(i + 1)?.timeMs ?: return@mapIndexedNotNull null
        if (line.words.size < 2 || line.words.any { it.timeMs != null }) return@mapIndexedNotNull null
        (next - line.timeMs).toDouble() / line.words.sumOf(::weight)
    }.sorted()
    if (samples.isEmpty()) return DEFAULT_PACE_MS
    return samples[(samples.size * 0.3).toInt().coerceAtMost(samples.size - 1)].coerceIn(MIN_PACE_MS, MAX_PACE_MS)
}

/**
 * Every word with its time. Enhanced (word-by-word) timings are used as given. Otherwise each line's
 * words are spread by how long each takes to sing (its syllables), at the song's own pace, so short
 * words pass quickly, long ones take longer, and a line followed by a pause isn't drawn out across it.
 */
fun Lyrics.timedWords(): List<TimedWord> {
    val pace = pace()
    val out = mutableListOf<TimedWord>()
    lines.forEachIndexed { i, line ->
        if (line.words.isEmpty()) return@forEachIndexed
        val next = lines.getOrNull(i + 1)?.timeMs ?: (line.timeMs + MAX_LINE_MS)
        val total = line.words.sumOf(::weight)
        val gap = (next - line.timeMs).coerceIn(0, MAX_LINE_MS).toDouble()
        val span = minOf(gap * LINE_FILL, total * pace * PACE_SLACK)
        var before = 0.0
        var previous = line.timeMs
        line.words.forEach { word ->
            val time = (word.timeMs ?: (line.timeMs + (span * before / total).toLong())).coerceAtLeast(previous)
            out += TimedWord(i, word.text, time)
            previous = time
            before += weight(word)
        }
    }
    return out
}

/** A gap between words at least this long is an instrumental break. */
const val LYRICS_BREAK_MS = 15_000L

/** The web comes back this long before the next word, so its first word doesn't pop in cold. */
const val LYRICS_LEAD_IN_MS = 1_500L

/** After the last word before a break, its line stays on screen this long. */
const val LYRICS_HOLD_MS = 6_000L

/** A stretch with nothing to sing: before the first word, a break mid-song, or after the last word. */
data class LyricsWait(val kind: Kind, val untilNextMs: Long?) {
    enum class Kind { Intro, Break, Outro }
}

/**
 * Whether [positionMs] falls in the intro, a long break or the outro, when the web would be blank
 * or frozen. Null while words are due. After a break's (or the song's) last word, its line stays up
 * for [LYRICS_HOLD_MS] so it can be read to the end.
 */
fun List<TimedWord>.waitAt(positionMs: Long): LyricsWait? {
    if (isEmpty()) return null
    val count = countAt(positionMs)
    val last = getOrNull(count - 1)?.timeMs
    val next = getOrNull(count)?.timeMs
    if (next == null) {
        return if (positionMs - last!! >= LYRICS_HOLD_MS) LyricsWait(LyricsWait.Kind.Outro, null) else null
    }
    val untilNext = next - positionMs
    if (untilNext <= LYRICS_LEAD_IN_MS) return null
    if (last == null) return LyricsWait(LyricsWait.Kind.Intro, untilNext)
    val isBreak = next - last >= LYRICS_BREAK_MS && positionMs - last >= LYRICS_HOLD_MS
    return if (isBreak) LyricsWait(LyricsWait.Kind.Break, untilNext) else null
}

/** How many of these words have been sung by [positionMs] (they are in time order). */
fun List<TimedWord>.countAt(positionMs: Long): Int {
    var lo = 0
    var hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid].timeMs <= positionMs) lo = mid + 1 else hi = mid
    }
    return lo
}
