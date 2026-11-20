package com.boostdriver.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

internal class BoostEngine(private val factory: (Int) -> Effect) : AutoCloseable {
    interface Effect : AutoCloseable {
        fun apply(millibels: Int)
    }

    private var effect: Effect? = null
    private var kind = 0

    fun apply(percent: Int): Int {
        val gain = millibels(percent)
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
            } catch (_: RuntimeException) {
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
        fun millibels(percent: Int): Int = percent.coerceIn(0, 60) * 750 / 100

        @Suppress("DEPRECATION") // Session zero is the legacy output mix.
        fun platformEffect(kind: Int, lostControl: () -> Unit): Effect {
            val fx = if (kind == 1) LoudnessEnhancer(0) else Equalizer(0, 0)
            try {
                check(fx.hasControl() && !fx.enabled)
                fx.setControlStatusListener { _, control -> if (!control) lostControl() }
            } catch (failure: RuntimeException) {
                fx.release()
                throw failure
            }
            return object : Effect {
                override fun apply(millibels: Int) {
                    check(fx.hasControl())
                    when (fx) {
                        is LoudnessEnhancer -> fx.setTargetGain(millibels)
                        is Equalizer -> {
                            val range = fx.bandLevelRange
                            val bands = fx.numberOfBands.toInt()
                            check(range[1] > 0 && bands > 0)
                            val level = millibels.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                            repeat(bands) { fx.setBandLevel(it.toShort(), level) }
                        }
                    }
                    check(fx.setEnabled(true) == AudioEffect.SUCCESS)
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
