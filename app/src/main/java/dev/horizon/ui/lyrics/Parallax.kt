package dev.horizon.ui.lyrics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/** Gravity is about this strong; a change of [TILT_FULL_SCALE] of it reads as a full tilt. */
private const val GRAVITY = SensorManager.GRAVITY_EARTH

/** About 15 degrees of tilt moves the parallax all the way. */
private const val TILT_FULL_SCALE = 0.26f

/** How quickly the tilt follows the sensor (per reading): smooths out hand shake. */
private const val TILT_SMOOTHING = 0.12f

/** How quickly "level" drifts to however the phone is resting (per reading), so a docked phone settles. */
private const val REST_DRIFT = 0.004f

/**
 * How the phone is tilted away from how it's resting, in screen directions: [x] positive when the
 * right edge dips, [y] positive when the top tips away. Each runs -1..1. "Resting" slowly follows
 * the phone, so on a charger stand it settles back to 0 and the galaxy sits still; nudging or
 * picking the phone up moves it. Read each frame while drawing.
 */
internal class Tilt {
    @Volatile var x = 0f
        internal set

    @Volatile var y = 0f
        internal set
}

/**
 * The phone's tilt, from the accelerometer (no permission needed), measured only while this is on
 * screen. With [enabled] false (reduce motion on) it stays at rest.
 */
@Composable
internal fun rememberTilt(enabled: Boolean): Tilt {
    val tilt = remember { Tilt() }
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(enabled, context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || sensors == null || sensor == null) {
            tilt.x = 0f
            tilt.y = 0f
            return@DisposableEffect onDispose { }
        }
        var restX = Float.NaN
        var restY = 0f
        var gx = 0f
        var gy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Device axes to screen axes for however the screen is turned.
                val ax = event.values[0]
                val ay = event.values[1]
                val (sx, sy) = when (view.display?.rotation ?: Surface.ROTATION_0) {
                    Surface.ROTATION_90 -> -ay to ax
                    Surface.ROTATION_180 -> -ax to -ay
                    Surface.ROTATION_270 -> ay to -ax
                    else -> ax to ay
                }
                // Raw accelerometer readings include every jolt; smooth them toward gravity.
                gx += (sx - gx) * 0.25f
                gy += (sy - gy) * 0.25f
                if (restX.isNaN()) {
                    restX = gx
                    restY = gy
                }
                restX += (gx - restX) * REST_DRIFT
                restY += (gy - restY) * REST_DRIFT
                val tx = (-(gx - restX) / GRAVITY / TILT_FULL_SCALE).coerceIn(-1f, 1f)
                val ty = ((gy - restY) / GRAVITY / TILT_FULL_SCALE).coerceIn(-1f, 1f)
                tilt.x += (tx - tilt.x) * TILT_SMOOTHING
                tilt.y += (ty - tilt.y) * TILT_SMOOTHING
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensors.unregisterListener(listener) }
    }
    return tilt
}
