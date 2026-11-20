package com.boostdriver.audio;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;

public final class BoostEngine implements AutoCloseable {
    public interface Effect extends AutoCloseable {
        void apply(int millibels);
        @Override void close();
    }
    public interface Factory { Effect create(int kind); }
    private final Factory factory;
    private Effect effect;
    private int kind;

    public BoostEngine(Factory factory) { this.factory = factory; }

    public static int millibels(int percent) {
        return Math.max(0, Math.min(60, percent)) * 750 / 100;
    }

    public int apply(int percent) {
        int gain = millibels(percent);
        if (gain == 0) { close(); return 0; }
        for (int next = effect == null ? 1 : kind; next <= 2; next++) {
            try {
                if (effect == null) effect = factory.create(next);
                effect.apply(gain);
                return kind = next;
            } catch (RuntimeException failure) {
                close();
            }
        }
        return 0;
    }

    @Override public void close() {
        if (effect != null) {
            try { effect.close(); } catch (RuntimeException ignored) { /* efeito já perdido */ }
            effect = null;
        }
        kind = 0;
    }

    @SuppressWarnings("deprecation") // Sessão 0: mix global legado, não suportado por todos os aparelhos.
    public static Effect platformEffect(int kind, Runnable lostControl) {
        AudioEffect fx = kind == 1 ? new LoudnessEnhancer(0) : new Equalizer(0, 0);
        try {
            if (!fx.hasControl() || fx.getEnabled()) throw new IllegalStateException();
            fx.setControlStatusListener((effect, control) -> { if (!control) lostControl.run(); });
        } catch (RuntimeException failure) {
            fx.release();
            throw failure;
        }
        return new Effect() {
            @Override public void apply(int gain) {
                if (!fx.hasControl()) throw new IllegalStateException();
                if (fx instanceof LoudnessEnhancer loudness) {
                    loudness.setTargetGain(gain);
                } else if (fx instanceof Equalizer equalizer) {
                    short[] range = equalizer.getBandLevelRange();
                    if (range[1] <= 0 || equalizer.getNumberOfBands() <= 0) throw new IllegalStateException();
                    short level = (short) Math.max(range[0], Math.min(gain, range[1]));
                    for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
                        equalizer.setBandLevel(band, level);
                    }
                }
                if (fx.setEnabled(true) != AudioEffect.SUCCESS) throw new IllegalStateException();
            }
            @Override public void close() {
                try {
                    fx.setControlStatusListener(null);
                    if (fx.hasControl()) fx.setEnabled(false);
                } finally { fx.release(); }
            }
        };
    }
}
