package com.boostdriver

import com.boostdriver.audio.BoostEngine
import com.boostdriver.audio.PlaybackSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostTest {
    @Test fun selectionDefaultsToTwentyAndPreservesExplicitZero() {
        assertEquals(20, BoostPrefs.resolveSelection(null, 0))
        assertEquals(30, BoostPrefs.resolveSelection(null, 30))
        assertEquals(0, BoostPrefs.resolveSelection(0, 30))
        assertEquals(100, BoostPrefs.resolveSelection(120, 0))
        assertEquals(80, BoostPrefs.resolveSelection(80, 0))
    }

    @Test fun disabledChangesDoNotStartAndStaleRequestsCannotEnable() {
        val requests = BoostRequests()
        assertNull(requests.update())
        assertNull(requests.enable(0))
        val first = requireNotNull(requests.enable(20))
        assertTrue(requests.accepts(first))
        val update = requireNotNull(requests.update())
        assertFalse(requests.accepts(first))
        requests.disable()
        assertFalse(requests.accepts(update))
        val second = requireNotNull(requests.enable(20))
        assertTrue(requests.accepts(second))
        assertFalse(requests.accepts(first))
    }

    @Test fun announcedSessionsAreBoundedAndCloseRequiresMatchingOpen() {
        val sessions = PlaybackSessions(capacity = 2)
        assertFalse(sessions.open(0, "test.player"))
        assertFalse(sessions.open(42, null))
        assertTrue(sessions.open(42, "test.player"))
        assertFalse(sessions.open(42, "another.player"))
        assertTrue(sessions.open(43, "another.player"))
        assertFalse(sessions.open(44, "third.player"))
        assertFalse(sessions.close(42, "another.player"))
        assertTrue(sessions.close(42, "test.player"))
        assertEquals(setOf(43), sessions.ids)
        sessions.clear()
        assertTrue(sessions.ids.isEmpty())
    }

    @Test fun gainConversionIsBounded() {
        assertEquals(0, BoostEngine.millibels(-10))
        assertEquals(1200, BoostEngine.millibels(40))
        assertEquals(2587, BoostEngine.millibels(100))
    }

    @Test fun explicitCalibrationTargetIsBoundedAndDoesNotChangeTheSliderCurve() {
        val targets = mutableListOf<Int>()
        BoostEngine {
            object : BoostEngine.Effect {
                override fun apply(millibels: Int) { targets.add(millibels) }
                override fun close() = Unit
            }
        }.use { engine ->
            assertEquals(1, engine.applyTarget(150))
            assertEquals(1, engine.applyTarget(562))
            assertEquals(1, engine.applyTarget(1125))
            assertEquals(1, engine.applyTarget(Int.MAX_VALUE))
            assertEquals(0, engine.applyTarget(-1))
            assertEquals(listOf(150, 562, 1125, 2587), targets)
        }
    }

    @Test fun calibrationTargetsAreMonotonicAndMatchesReferenceAtEighty() {
        val targets = (-1..101).map { BoostEngine.millibels(it) }
        assertTrue(targets.zipWithNext().all { (previous, next) -> previous <= next })
        assertEquals(0, targets.first())
        assertEquals(600, BoostEngine.millibels(20))
        assertEquals(1200, BoostEngine.millibels(40))
        assertEquals(2587, BoostEngine.millibels(80))
        assertEquals(2587, BoostEngine.millibels(100))
        assertEquals(2587, targets.last())
    }

    @Test fun bootRequiresOptInAndANewlyArmedGain() {
        assertEquals(0, BoostPrefs.bootGain(false, 40, 40))
        assertEquals(0, BoostPrefs.bootGain(true, 0, 80))
        assertEquals(0, BoostPrefs.bootGain(true, 40, 0))
        assertEquals(80, BoostPrefs.bootGain(true, 80, 80))
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
            assertEquals(2, engine.applyTarget(150))
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
