package dev.horizon.lyrics

/** Titles longer than this are rarely sung word for word; not worth looking for. */
private const val MAX_TITLE_WORDS = 6

/** A word as compared with the title: lower case, letters and digits only ("Love," and "love" match). */
internal fun titleKey(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/**
 * Where the song's own [title] is sung: each run of word indexes that spells it out in order
 * (case and punctuation ignored). Bits like "(Official Video)" are dropped from the title first.
 * A one-word title shorter than three letters ("Up", "I") is too common to mean anything, so it
 * never matches.
 */
fun List<TimedWord>.titleRuns(title: String): List<IntRange> {
    val want = TitleCleaner.clean(title, null).title.split(' ').map(::titleKey).filter { it.isNotEmpty() }
    if (want.isEmpty() || want.size > MAX_TITLE_WORDS) return emptyList()
    if (want.size == 1 && want[0].length < 3) return emptyList()
    val keys = map { titleKey(it.text) }
    val runs = ArrayList<IntRange>()
    var i = 0
    while (i + want.size <= size) {
        if (want.indices.all { keys[i + it] == want[it] }) {
            runs += i until i + want.size
            i += want.size
        } else {
            i++
        }
    }
    return runs
}
