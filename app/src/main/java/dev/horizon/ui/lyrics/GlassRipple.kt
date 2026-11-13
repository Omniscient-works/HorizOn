package dev.horizon.ui.lyrics

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Where a ripple starts on screen, and how far through it is (0..1). */
internal class Ripple(val centre: Offset, val progress: Float, val strength: Float)

/**
 * A ripple moving through glass: a ring travels out from the centre and bends whatever is drawn
 * beneath it (the lyrics) back and forth, with a faint shine on its crest. It weakens as it spreads.
 */
private const val RIPPLE_AGSL = """
uniform shader content;
uniform float2 centre;
uniform float progress;
uniform float strength;
uniform float px;

half4 main(float2 p) {
    float2 d = p - centre;
    float r = length(d);
    float front = progress * 260.0 * px;
    float x = (r - front) / (34.0 * px);
    float envelope = exp(-x * x) * (1.0 - progress) * strength;
    float wave = sin(x * 6.2831853);
    float2 dir = r > 0.5 ? d / r : float2(0.0, 0.0);
    // A second, weaker ring follows the first, like water.
    float x2 = (r - front * 0.62) / (26.0 * px);
    float envelope2 = exp(-x2 * x2) * (1.0 - progress) * strength * 0.45;
    float wave2 = sin(x2 * 6.2831853);
    float push = wave * envelope * 11.0 * px + wave2 * envelope2 * 7.0 * px;
    float4 c = float4(content.eval(p - dir * push));
    float shine = (max(wave, 0.0) * envelope + max(wave2, 0.0) * envelope2) * 0.16;
    return half4(float4(c.rgb + float3(shine), min(1.0, c.a + shine)));
}
"""

/** Builds the glass ripple, on Android 13 and later where custom shaders exist; null before that. */
internal class GlassRipple private constructor(private val shader: Any) {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun effect(ripple: Ripple, density: Float): androidx.compose.ui.graphics.RenderEffect {
        val s = shader as RuntimeShader
        s.setFloatUniform("centre", ripple.centre.x, ripple.centre.y)
        s.setFloatUniform("progress", ripple.progress)
        s.setFloatUniform("strength", ripple.strength)
        s.setFloatUniform("px", density)
        return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }

    companion object {
        /** The shader compiles on the phone; if that ever fails, the plain rings are drawn instead. */
        fun createOrNull(): GlassRipple? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            return try {
                GlassRipple(RuntimeShader(RIPPLE_AGSL))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
