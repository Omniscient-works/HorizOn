package dev.horizon.ui.lyrics

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb

/**
 * Volumetric gas clouds for the galaxy. Density comes from layered noise shaped by the same
 * spiral the stars follow, so the clouds lie along the arms and turn with them. They're lit from
 * the core: each point compares its density with the density a step closer to the core, so the
 * faces turned toward the light glow and the far sides fall into shadow, which is what makes them
 * read as volumes rather than flat smudges. The colour runs from the pale core colour near the
 * middle to the arm colours further out. Everything outside the disc is fully transparent, so the
 * black around it stays true OLED black.
 *
 * Drawn in two halves like the stars: the far half behind the planet, the near half in front.
 */
private const val CLOUDS_AGSL = """
uniform float2 centre;
uniform float radius;
uniform float inner;
uniform float tilt;
uniform float spin;
uniform float time;
uniform float beat;
uniform float side;
layout(color) uniform half3 coreColour;
layout(color) uniform half3 armColour;
layout(color) uniform half3 rimColour;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 4; k++) {
        v += a * noise(p);
        p = p * 2.03 + float2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

// Cloud density at a point on the disc, in disc space (the tilt undone).
float density(float2 q) {
    float r = length(q);
    float rn = clamp((r - inner) / max(radius - inner, 1.0), 0.0, 1.2);
    float theta = atan(q.y, q.x);
    float turn = spin * (1.3 - 0.6 * rn);
    // Two arms, the same spiral the stars follow.
    float phase = theta - log(1.0 + rn * 8.0) * 2.9 - turn;
    float arm = pow(0.5 + 0.5 * cos(2.0 * phase), 3.0);
    // Noise in a frame that turns with the galaxy, so the clouds swirl with it and slowly churn.
    float c = cos(-turn);
    float s = sin(-turn);
    float2 uv = float2(c * q.x - s * q.y, s * q.x + c * q.y) / radius * 2.6;
    float n = fbm(uv + float2(time * 0.02, -time * 0.015));
    float d = smoothstep(0.34, 0.8, n) * (0.12 + 0.88 * arm);
    // Thickest through the middle of the disc, thinning to nothing at the rim and around the core.
    d *= smoothstep(1.05, 0.5, rn) * smoothstep(-0.05, 0.22, rn);
    return d;
}

half4 main(float2 p) {
    float2 q = p - centre;
    q.y /= tilt;
    float r = length(q);
    if (r > radius * 1.1) return half4(0.0);
    // Far half (side -1) or near half (+1), blended softly across the middle line.
    float k = radius * 0.04;
    float mask = side > 0.0 ? smoothstep(-k, k, q.y) : 1.0 - smoothstep(-k, k, q.y);
    if (mask <= 0.001) return half4(0.0);

    float d = density(q);
    if (d <= 0.002) return half4(0.0);
    // Light from the core: brighter where the cloud thins toward it, shadowed behind its bulk.
    float2 toCore = r > 1.0 ? -q / r : float2(0.0, 0.0);
    float dNear = density(q + toCore * radius * 0.06);
    float lit = clamp(0.55 + (d - dNear) * 3.5, 0.15, 1.4);

    float rn = clamp((r - inner) / max(radius - inner, 1.0), 0.0, 1.0);
    float3 base = mix(float3(armColour), float3(rimColour), smoothstep(0.35, 1.0, rn));
    float3 colour = mix(base, float3(coreColour), exp(-rn * 3.0) * 0.8);
    // Scattered light: the core glows through the near side of the clouds.
    colour *= 0.5 + 0.75 * lit;
    float alpha = clamp(d * (0.68 + 0.25 * beat) * mask, 0.0, 0.85);
    return half4(half3(colour * alpha), half(alpha));
}
"""

/** The cloud shader, on Android 13 and later where custom shaders exist; null before that. */
internal class GalaxyClouds private constructor(private val shader: Any) {
    private val brush = object : ShaderBrush() {
        override fun createShader(size: Size) = shader as android.graphics.Shader
    }

    /** Set if drawing ever fails on this phone; the soft nebula blobs take over from then on. */
    var failed = false
        private set

    /**
     * Draws one half ([front] or far) of the clouds for a galaxy at [centre] of [radius]. Returns
     * false if it couldn't, so the caller can draw the plain blobs instead.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun draw(
        scope: DrawScope,
        centre: Offset,
        radius: Float,
        inner: Float,
        tilt: Float,
        spin: Float,
        time: Float,
        beat: Float,
        palette: List<Color>,
        front: Boolean,
    ): Boolean {
        if (failed) return false
        try {
            drawOrThrow(scope, centre, radius, inner, tilt, spin, time, beat, palette, front)
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        return !failed
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawOrThrow(
        scope: DrawScope,
        centre: Offset,
        radius: Float,
        inner: Float,
        tilt: Float,
        spin: Float,
        time: Float,
        beat: Float,
        palette: List<Color>,
        front: Boolean,
    ) {
        val s = shader as RuntimeShader
        s.setFloatUniform("centre", centre.x, centre.y)
        s.setFloatUniform("radius", radius)
        s.setFloatUniform("inner", inner)
        s.setFloatUniform("tilt", tilt)
        s.setFloatUniform("spin", spin)
        s.setFloatUniform("time", time)
        s.setFloatUniform("beat", beat)
        s.setFloatUniform("side", if (front) 1f else -1f)
        s.setColorUniform("coreColour", palette[0].toArgb())
        s.setColorUniform("armColour", palette[2].toArgb())
        s.setColorUniform("rimColour", palette[3].toArgb())
        val half = radius * 1.1f
        scope.drawRect(brush, topLeft = Offset(centre.x - half, centre.y - half * tilt), size = Size(half * 2, half * 2 * tilt))
    }

    companion object {
        /** The shader compiles on the phone; if that ever fails, the soft nebula blobs are drawn instead. */
        fun createOrNull(): GalaxyClouds? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            return try {
                GalaxyClouds(RuntimeShader(CLOUDS_AGSL))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
