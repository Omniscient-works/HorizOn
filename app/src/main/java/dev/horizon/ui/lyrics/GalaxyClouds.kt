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
 * Seen through the galaxy's camera with perspective and sampled at three heights through the disc,
 * so the clouds have depth. Drawn in two halves like the stars: the far half behind the planet, the
 * near half in front.
 */
private const val CLOUDS_AGSL = """
uniform float2 centre;
uniform float radius;
uniform float inner;
uniform float sinE;
uniform float cosE;
uniform float camDist;
uniform float thick;
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
    // Undo the camera: for each of three heights through the disc, find where the ray through this
    // pixel crosses that slice, and how thick the cloud is there. Compositing the slices front to
    // back gives the clouds real depth: they shift against each other as the camera sways.
    float sx = p.x - centre.x;
    float Y = centre.y - p.y;
    float denom = camDist * sinE - Y * cosE;
    if (denom <= 1.0) return half4(0.0);
    float k = radius * 0.05;
    float3 colour = float3(0.0);
    float alpha = 0.0;
    float lit = 1.0;
    for (int i = 0; i < 3; i++) {
        float h = (1.0 - float(i)) * thick * 0.7; // near slice (higher) first
        float z = (Y * (camDist - h * sinE) - h * camDist * cosE) / denom;
        float depth = z * cosE - h * sinE;
        float f = camDist / (camDist + depth);
        float2 q = float2(sx / f, -z);
        float r = length(q);
        if (r > radius * 1.1) continue;
        // Far half (side -1, behind the planet) or near half (+1), blended across the middle.
        float mask = side > 0.0 ? 1.0 - smoothstep(-k, k, depth) : smoothstep(-k, k, depth);
        if (mask <= 0.001) continue;
        float rn = clamp((r - inner) / max(radius - inner, 1.0), 0.0, 1.0);
        // The disc is thicker at the core (the bulge): a slice off the plane holds less cloud.
        float t = thick * (0.35 + exp(-rn * 4.5));
        float w = exp(-(h * h) / (t * t));
        float d = density(q) * w;
        if (d <= 0.002) continue;
        if (i == 1) {
            // Light from the core: brighter where the cloud thins toward it, shadowed behind its bulk.
            float2 toCore = r > 1.0 ? -q / r : float2(0.0, 0.0);
            float dNear = density(q + toCore * radius * 0.06);
            lit = clamp(0.55 + (d - dNear) * 3.5, 0.15, 1.4);
        }
        float3 base = mix(float3(armColour), float3(rimColour), smoothstep(0.35, 1.0, rn));
        float3 c = mix(base, float3(coreColour), exp(-rn * 3.0) * 0.8) * (0.5 + 0.75 * lit);
        // Nearer slices a touch brighter, like the stars.
        float a = clamp(d * (0.62 + 0.18 * beat) * mask * clamp(f * f, 0.5, 1.3), 0.0, 0.7);
        colour += (1.0 - alpha) * c * a;
        alpha += (1.0 - alpha) * a;
    }
    return half4(half3(colour), half(min(alpha, 0.85)));
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
        cam: GalaxyCamera,
        radius: Float,
        inner: Float,
        spin: Float,
        time: Float,
        beat: Float,
        palette: List<Color>,
        front: Boolean,
    ): Boolean {
        if (failed) return false
        try {
            drawOrThrow(scope, cam, radius, inner, spin, time, beat, palette, front)
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        return !failed
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawOrThrow(
        scope: DrawScope,
        cam: GalaxyCamera,
        radius: Float,
        inner: Float,
        spin: Float,
        time: Float,
        beat: Float,
        palette: List<Color>,
        front: Boolean,
    ) {
        val s = shader as RuntimeShader
        val centre = cam.centre
        s.setFloatUniform("centre", centre.x, centre.y)
        s.setFloatUniform("radius", radius)
        s.setFloatUniform("inner", inner)
        s.setFloatUniform("sinE", cam.sinE)
        s.setFloatUniform("cosE", cam.cosE)
        s.setFloatUniform("camDist", cam.dist)
        s.setFloatUniform("thick", radius * 0.1f)
        s.setFloatUniform("spin", spin)
        s.setFloatUniform("time", time)
        s.setFloatUniform("beat", beat)
        s.setFloatUniform("side", if (front) 1f else -1f)
        s.setColorUniform("coreColour", palette[0].toArgb())
        s.setColorUniform("armColour", palette[2].toArgb())
        s.setColorUniform("rimColour", palette[3].toArgb())
        // The disc's outline on screen: the far edge is the top, the near edge (wider, nearer) the bottom.
        val edge = radius * 1.15f
        val slab = radius * 0.1f
        cam.project(0f, edge, slab)
        val top = cam.sy
        cam.project(0f, -edge, -slab)
        val bottom = cam.sy
        val wide = edge * cam.scale
        scope.drawRect(brush, topLeft = Offset(centre.x - wide, top), size = Size(wide * 2, bottom - top))
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
