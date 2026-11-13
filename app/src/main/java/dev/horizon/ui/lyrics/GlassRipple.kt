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
 * Liquid glass. The ripple is a moving height field: a drop lands (a lens that swells and settles
 * at the centre), then two rings run outward and weaken as they spread. Whatever is drawn beneath
 * (the lyrics) is refracted through its slope, so words bend and stretch as a ring passes, and the
 * three colours bend by slightly different amounts, so light splits into fringes at the edges, as
 * through real glass. A highlight catches the crests from the upper left.
 */
private const val RIPPLE_AGSL = """
uniform shader content;
uniform float2 centre;
uniform float progress;
uniform float strength;
uniform float px;

// Height of the glass surface at distance r from the centre.
float height(float r) {
    float front = progress * 300.0 * px;
    float fade = (1.0 - progress) * strength;
    float x1 = (r - front) / (40.0 * px);
    float x2 = (r - front * 0.6) / (30.0 * px);
    float ring1 = sin(x1 * 6.2831853) * exp(-x1 * x1);
    float ring2 = sin(x2 * 6.2831853) * exp(-x2 * x2) * 0.55;
    // The drop itself: a lens over the word that swells, then settles as the rings leave.
    float lens = exp(-r * r / (70.0 * 70.0 * px * px)) * sin(min(progress * 3.0, 1.0) * 3.1415927) * 0.8;
    return (ring1 + ring2 + lens) * fade;
}

half4 main(float2 p) {
    float2 d = p - centre;
    float r = length(d);
    float2 dir = r > 0.5 ? d / r : float2(0.0, 0.0);
    // The slope of the surface bends the light: steeper, more bending.
    float e = 1.5 * px;
    float slope = (height(r + e) - height(r - e)) / (2.0 * e);
    float bend = slope * (40.0 * px) * (0.95 * px);
    float2 shift = dir * bend;
    // Red bends least, blue most: the colours of a word spread apart at the edges of a ring.
    float4 cr = float4(content.eval(p - shift * 0.8));
    float4 cg = float4(content.eval(p - shift));
    float4 cb = float4(content.eval(p - shift * 1.22));
    float4 c = float4(cr.r, cg.g, cb.b, max(cr.a, max(cg.a, cb.a)));
    // Light from the upper left catches the side of each crest that faces it.
    float facing = max(dot(-dir, normalize(float2(-0.6, -0.8))) * slope * 40.0 * px, 0.0);
    float shine = pow(min(facing, 1.0), 6.0) * 0.08;
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
