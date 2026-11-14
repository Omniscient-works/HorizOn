package dev.horizon.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.ui.theme.Amber
import dev.horizon.ui.theme.Ink
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper
import dev.horizon.ui.theme.PillSurface

/** One stop on the first-launch tour. Each asks for a gesture and moves on once it's been done. */
enum class TourStep { Lyrics, Back, Clock, Controls, Done }

/** What the tour needs to know about the screen right now. */
data class TourScreen(
    val onLyrics: Boolean,
    val onClock: Boolean,
    val controlsOpen: Boolean,
    val hasMusic: Boolean,
    val canShowClock: Boolean,
)

/** The tour's order and when each step counts as done, kept apart from the drawing so it can be tested. */
object Tour {
    /** Whether the user has just done what [step] asks for. */
    fun isDone(step: TourStep, screen: TourScreen): Boolean = when (step) {
        // With nothing playing there's no lyrics page to swipe to; that step waits for "Next".
        TourStep.Lyrics -> screen.hasMusic && screen.onLyrics
        TourStep.Back -> !screen.onLyrics && !screen.onClock
        TourStep.Clock -> screen.onClock
        TourStep.Controls -> screen.controlsOpen
        TourStep.Done -> false
    }

    /** The step after [step], skipping ones this screen can't show. */
    fun next(step: TourStep, screen: TourScreen): TourStep = when (step) {
        TourStep.Lyrics -> if (screen.hasMusic) TourStep.Back else afterBack(screen)
        TourStep.Back -> afterBack(screen)
        TourStep.Clock -> TourStep.Controls
        TourStep.Controls, TourStep.Done -> TourStep.Done
    }

    private fun afterBack(screen: TourScreen) = if (screen.canShowClock) TourStep.Clock else TourStep.Controls
}

/**
 * The tour's card and its animated hint. The card sits at the top, out of the way of the controls,
 * and only its own buttons take touches: swipes and taps everywhere else reach the screen beneath,
 * which is the point, since each step is learned by doing it.
 */
@Composable
fun TourOverlay(
    step: TourStep,
    hasMusic: Boolean,
    lyricsOnline: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (step) {
            TourStep.Lyrics -> if (hasMusic) SwipeHint(towardLeft = true, Modifier.align(Alignment.Center))
            TourStep.Back, TourStep.Clock -> SwipeHint(towardLeft = false, Modifier.align(Alignment.Center))
            TourStep.Controls -> TapHint(Modifier.align(Alignment.Center))
            TourStep.Done -> Unit
        }
        AnimatedContent(
            step,
            transitionSpec = { fadeIn(tween(350, delayMillis = 150)) togetherWith fadeOut(tween(200)) },
            label = "tour",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
        ) { current ->
            val (title, body) = words(current, hasMusic, lyricsOnline)
            val button = when {
                current == TourStep.Done -> "Got it"
                current == TourStep.Lyrics && !hasMusic -> "Next"
                else -> null
            }
            TourCard(title, body, current, button, onButton = if (current == TourStep.Done) onSkip else onNext, onSkip = onSkip)
        }
    }
}

private fun words(step: TourStep, hasMusic: Boolean, lyricsOnline: Boolean): Pair<String, String> {
    val third = if (lyricsOnline) "the song's lyrics, or a visualizer when there are none" else "a visualizer for the song"
    return when (step) {
        TourStep.Lyrics -> if (hasMusic) {
            "Swipe left" to "for $third."
        } else {
            "Lyrics are one swipe to the left" to "Play some music, then swipe left for $third."
        }
        TourStep.Back -> "Swipe right" to "to come back to the clock and player."
        TourStep.Clock -> "Swipe right again" to "for the full-screen clock."
        TourStep.Controls -> "Tap anywhere" to "for the controls: clock style, directions, lyrics and Close."
        TourStep.Done -> "You're all set" to "Double-tap the player or lyrics to open your music app. You can replay this tour from the controls."
    }
}

@Composable
private fun TourCard(title: String, body: String, step: TourStep, button: String?, onButton: () -> Unit, onSkip: () -> Unit) {
    val pill = RoundedCornerShape(50)
    Column(
        Modifier
            .widthIn(max = 440.dp)
            .background(PillSurface, RoundedCornerShape(20.dp))
            // The card's own background swallows taps, so tapping it doesn't open the controls.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(title, color = Amber, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Paper, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Where we are in the tour.
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TourStep.entries.forEach {
                    Box(Modifier.size(if (it == step) 7.dp else 5.dp).background(if (it == step) Amber else Muted.copy(alpha = 0.5f), CircleShape))
                }
            }
            Spacer(Modifier.weight(1f))
            if (step != TourStep.Done) {
                Box(Modifier.clickable(role = Role.Button, onClick = onSkip).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Skip tour", color = Muted, fontSize = 13.sp)
                }
            }
            if (button != null) {
                Box(
                    Modifier
                        .background(Paper, pill)
                        .clickable(role = Role.Button, onClick = onButton)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(button, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A glowing fingertip sliding across the middle of the screen with chevrons ahead of it, on a loop. */
@Composable
private fun SwipeHint(towardLeft: Boolean, modifier: Modifier) {
    var frame by remember { mutableLongStateOf(0L) }
    val start = remember { SystemClock.elapsedRealtime() }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frame = it }
    }
    Canvas(modifier.size(width = 260.dp, height = 80.dp)) {
        if (frame < 0L) return@Canvas
        val cycle = 2_000f
        val k = ((SystemClock.elapsedRealtime() - start) % cycle.toLong()) / 1_400f // pauses for the last 0.6 s
        if (k > 1f) return@Canvas
        val dir = if (towardLeft) -1f else 1f
        val eased = 1f - (1f - k) * (1f - k) * (1f - k)
        val span = size.width * 0.7f
        val y = size.height / 2
        val from = size.width / 2 - dir * span / 2
        val x = from + dir * span * eased
        val fade = (if (k < 0.15f) k / 0.15f else if (k > 0.8f) (1f - k) / 0.2f else 1f).coerceIn(0f, 1f)
        // The trail behind the fingertip.
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Amber.copy(alpha = 0.55f * fade)), startX = from, endX = x),
            Offset(from, y),
            Offset(x, y),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(Brush.radialGradient(listOf(Amber.copy(alpha = 0.45f * fade), Color.Transparent), Offset(x, y), 26.dp.toPx()), 26.dp.toPx(), Offset(x, y))
        drawCircle(Paper.copy(alpha = 0.9f * fade), 7.dp.toPx(), Offset(x, y))
        // Chevrons pointing the way.
        for (i in 0 until 2) {
            val cx = size.width / 2 + dir * (span / 2 + 18.dp.toPx() + i * 12.dp.toPx())
            val a = 8.dp.toPx()
            val c = Muted.copy(alpha = 0.5f * fade)
            drawLine(c, Offset(cx - dir * a, y - a), Offset(cx, y), 2.dp.toPx(), StrokeCap.Round)
            drawLine(c, Offset(cx - dir * a, y + a), Offset(cx, y), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** Rings pulsing out from the middle of the screen: tap here. */
@Composable
private fun TapHint(modifier: Modifier) {
    var frame by remember { mutableLongStateOf(0L) }
    val start = remember { SystemClock.elapsedRealtime() }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frame = it }
    }
    Canvas(modifier.size(120.dp)) {
        if (frame < 0L) return@Canvas
        val k = ((SystemClock.elapsedRealtime() - start) % 1_600L) / 1_600f
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Paper.copy(alpha = 0.85f * (1f - k * 0.5f)), (6f + 2f * (1f - k)) * density, c)
        for (i in 0 until 2) {
            val f = ((k + i * 0.5f) % 1f)
            drawCircle(Amber.copy(alpha = 0.6f * (1f - f)), (10f + 40f * f) * density, c, style = Stroke(2f * density))
        }
    }
}
