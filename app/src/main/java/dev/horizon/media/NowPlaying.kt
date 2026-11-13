package dev.horizon.media

import android.graphics.Bitmap
import dev.horizon.lyrics.TitleCleaner

/** A snapshot of what another app's media session is playing. */
data class NowPlaying(
    val packageName: String,
    val appLabel: String?,
    val title: String,
    val artist: String?,
    val art: Bitmap?,
    /** Track length, or 0 when the app doesn't report one (e.g. live streams). */
    val durationMs: Long,
    val isPlaying: Boolean,
    /** Position reported by the app at [positionUpdatedAt] (elapsedRealtime millis). */
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val playbackSpeed: Float,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    /** Whether the app accepts seeking (some live streams don't). */
    val canSeek: Boolean = false,
    /** Album name, when the app reports one (helps find the right lyrics). */
    val album: String? = null,
) {
    /**
     * The title as it's shown on screen: without the extras some apps pack into it, like
     * "[From GTAVI: Original Score] (feat. …)" or "(Official Video)". The full title is still
     * used where it matters, such as telling songs apart.
     */
    val displayTitle: String by lazy { TitleCleaner.clean(title, artist).title }

    /** Extrapolates the playback position to [nowElapsed], clamped to the track length. */
    fun positionAt(nowElapsed: Long): Long {
        val elapsed = if (isPlaying) ((nowElapsed - positionUpdatedAt) * playbackSpeed).toLong() else 0L
        val position = (positionMs + elapsed).coerceAtLeast(0L)
        return if (durationMs > 0) position.coerceAtMost(durationMs) else position
    }
}

/** Formats a track time as m:ss, or h:mm:ss for long tracks. */
fun formatTrackTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = (totalSeconds % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
}
