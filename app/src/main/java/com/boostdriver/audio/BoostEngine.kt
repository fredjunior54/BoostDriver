package com.boostdriver.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.boostdriver.BuildConfig

internal class BoostEngine(
    private val diagnostic: ((String) -> Unit)? = null,
    private val factory: (Int) -> Effect,
) : AutoCloseable {
    interface Effect : AutoCloseable {
        fun apply(millibels: Int)
    }

    private var effect: Effect? = null
    private var kind = 0

    fun apply(percent: Int): Int = applyTarget(millibels(percent))

    fun applyTarget(millibels: Int): Int {
        val gain = millibels.coerceIn(0, MAX_CALIBRATION_MB)
        if (gain == 0) {
            close()
            return 0
        }
        for (next in (if (effect == null) 1 else kind)..2) {
            try {
                val current = effect ?: factory(next).also { effect = it }
                current.apply(gain)
                kind = next
                return next
            } catch (failure: RuntimeException) {
                diagnostic?.invoke("Effect backend=$next targetMb=$gain rejected: ${failure.javaClass.simpleName}")
                close()
            }
        }
        return 0
    }

    override fun close() {
        try {
            effect?.close()
        } catch (_: RuntimeException) {
            // The audio framework may already have released the effect.
        } finally {
            effect = null
            kind = 0
        }
    }

    companion object {
        private const val MAX_CALIBRATION_MB = 2587
        const val CONFIRM_CALIBRATION_ABOVE_MB = 600

        fun millibels(percent: Int): Int = targetFor(percent, BuildConfig.AUDIO_CALIBRATION)

        fun targetFor(percent: Int, calibration: Boolean): Int =
            if (!calibration) {
                percent.coerceIn(0, 60) * 750 / 100
            } else if (percent <= 40) {
                percent.coerceAtLeast(0) * 30
            } else {
                1200 + (percent.coerceAtMost(80) - 40) * (MAX_CALIBRATION_MB - 1200) / 40
            }

        @Suppress("DEPRECATION") // Session zero is the legacy output mix.
        fun platformEffect(
            kind: Int,
            session: Int,
            diagnostic: ((String) -> Unit)? = null,
            lostControl: () -> Unit,
        ): Effect {
            val fx = if (kind == 1) LoudnessEnhancer(session) else Equalizer(0, session)
            try {
                check(fx.hasControl() && !fx.enabled)
                diagnostic?.invoke("Created ${fx.descriptor.name} enabled=${fx.enabled} hasControl=${fx.hasControl()}")
                fx.setControlStatusListener { _, control ->
                    diagnostic?.invoke("Control changed: hasControl=$control")
                    if (!control) lostControl()
                }
            } catch (failure: RuntimeException) {
                fx.release()
                throw failure
            }
            return object : Effect {
                override fun apply(millibels: Int) {
                    check(fx.hasControl())
                    val readback = when (fx) {
                        is LoudnessEnhancer -> {
                            fx.setTargetGain(millibels)
                            val target = fx.targetGain
                            if (target != millibels.toFloat()) diagnostic?.invoke("Gain readback mismatch: requested=$millibels actual=$target")
                            check(target == millibels.toFloat())
                            target.toString()
                        }
                        is Equalizer -> {
                            val range = fx.bandLevelRange
                            val bands = fx.numberOfBands.toInt()
                            check(range[1] > 0 && bands > 0)
                            val level = millibels.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                            repeat(bands) { fx.setBandLevel(it.toShort(), level) }
                            if (diagnostic == null) "" else runCatching {
                                List(bands) { fx.getBandLevel(it.toShort()) }.joinToString(",")
                            }.getOrDefault("unavailable")
                        }
                        else -> error("Unsupported effect backend")
                    }
                    val result = fx.setEnabled(true)
                    diagnostic?.invoke(
                        "Configured targetMb=$millibels readbackMb=$readback " +
                            "enableResult=$result enabled=${fx.enabled} hasControl=${fx.hasControl()}",
                    )
                    check(result == AudioEffect.SUCCESS && fx.enabled && fx.hasControl())
                }

                override fun close() {
                    try {
                        fx.setControlStatusListener(null)
                        if (fx.hasControl()) fx.setEnabled(false)
                    } finally {
                        fx.release()
                    }
                }
            }
        }
    }
}
