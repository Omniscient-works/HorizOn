package dev.horizon.lyrics

import kotlin.random.Random

/** The rare constellation in the lyrics: how rare it is, and a way for debug builds to force it. */
object ConstellationEgg {
    /** About one song in 500. Very rare on purpose: it should feel like a find. */
    const val CHANCE = 1f / 500

    /** Debug builds set this to show a constellation in the next song's lyrics. */
    @Volatile
    var forceNext: Boolean = false

    /** Rolled once per song: a constellation to build from its lyrics, or (almost always) none. */
    fun roll(random: Random = Random.Default): Constellation? {
        val forced = forceNext
        forceNext = false
        return if (forced || random.nextFloat() < CHANCE) Constellations.all.random(random) else null
    }
}
