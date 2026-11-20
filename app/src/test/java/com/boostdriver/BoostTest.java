package com.boostdriver;

import com.boostdriver.audio.BoostEngine;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoostTest {
    @Test public void gainConversionIsBounded() {
        assertEquals(0, BoostEngine.millibels(-10));
        assertEquals(300, BoostEngine.millibels(40));
        assertEquals(450, BoostEngine.millibels(100));
    }

    @Test public void bootRequiresConsentAndRespectsLimit() {
        assertEquals(0, BoostPrefs.bootGain(false, true, 40, 40));
        assertEquals(0, BoostPrefs.bootGain(true, false, 40, 40));
        assertEquals(0, BoostPrefs.bootGain(true, true, 0, 40));
        assertEquals(20, BoostPrefs.bootGain(true, true, 40, 20));
    }

    @Test public void fallbackAndZeroReleaseBothEffects() {
        int[] calls = new int[3];
        try (var engine = new BoostEngine(kind -> new BoostEngine.Effect() {
            @Override public void apply(int gain) {
                if (kind == 1) throw new IllegalStateException();
                calls[0] = gain;
            }
            @Override public void close() { calls[kind]++; }
        })) {
            assertEquals(2, engine.apply(20));
            assertEquals(150, calls[0]);
            assertEquals(1, calls[1]);
            assertEquals(0, engine.apply(0));
            assertEquals(1, calls[2]);
        }
        assertEquals(1, calls[2]);
    }

    @Test public void bothEffectsUnavailableLeavesBoostOff() {
        int[] attempts = {0};
        try (var engine = new BoostEngine(kind -> { attempts[0]++; throw new IllegalStateException(); })) {
            assertEquals(0, engine.apply(0));
            assertEquals(0, attempts[0]);
            assertEquals(0, engine.apply(20));
            assertEquals(2, attempts[0]);
        }
    }
}
