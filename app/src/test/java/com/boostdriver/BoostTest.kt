package com.boostdriver

import com.boostdriver.audio.BoostEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class BoostTest {
    @Test fun gainConversionIsBounded() {
        assertEquals(0, BoostEngine.millibels(-10))
        assertEquals(300, BoostEngine.millibels(40))
        assertEquals(450, BoostEngine.millibels(100))
    }

    @Test fun bootRequiresConsentAndRespectsLimit() {
        assertEquals(0, BoostPrefs.bootGain(false, true, 40, 40))
        assertEquals(0, BoostPrefs.bootGain(true, false, 40, 40))
        assertEquals(0, BoostPrefs.bootGain(true, true, 0, 40))
        assertEquals(20, BoostPrefs.bootGain(true, true, 40, 20))
    }

    @Test fun fallbackAndZeroReleaseBothEffects() {
        val calls = IntArray(3)
        BoostEngine { kind ->
            object : BoostEngine.Effect {
                override fun apply(millibels: Int) {
                    check(kind != 1)
                    calls[0] = millibels
                }
                override fun close() { calls[kind]++ }
            }
        }.use { engine ->
            assertEquals(2, engine.apply(20))
            assertEquals(150, calls[0])
            assertEquals(1, calls[1])
            assertEquals(0, engine.apply(0))
            assertEquals(1, calls[2])
        }
        assertEquals(1, calls[2])
    }

    @Test fun bothEffectsUnavailableLeavesBoostOff() {
        var attempts = 0
        BoostEngine { attempts++; error("Effect unavailable") }.use { engine ->
            assertEquals(0, engine.apply(0))
            assertEquals(0, attempts)
            assertEquals(0, engine.apply(20))
            assertEquals(2, attempts)
        }
    }
}
