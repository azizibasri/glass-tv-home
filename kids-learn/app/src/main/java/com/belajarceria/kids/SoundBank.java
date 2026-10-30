package com.belajarceria.kids;

import java.util.Random;

/**
 * The music corner's instruments, synthesised from how the real ones vibrate (no recordings in the APK), tuned in
 * equal temperament to A4 = 440 Hz. Each method returns one note as 16-bit mono samples at {@link #RATE}.
 * Plain Java with no Android in it, so the tuning can be measured off the device.
 */
final class SoundBank {
    static final int RATE = 44100;

    private SoundBank() {}

    /** Equal temperament: MIDI 69 is A4 at 440 Hz, every semitone a factor of 2^(1/12). */
    static double freq(int midi) {
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }

    // ---------------------------------------------------------------- piano

    /**
     * A piano note: up to 14 partials, stretched sharp by the strings' stiffness (inharmonicity), each a pair of
     * strings a cent apart so they beat gently, with the two-stage decay of a real piano (a quick drop, then a long
     * sustain) and the felt hammer's thump at the start. Higher notes are brighter and die away sooner.
     */
    static short[] piano(int midi) {
        double f = freq(midi);
        double dur = Math.max(1.2, 3.2 - (midi - 48) * 0.05);
        int n = (int) (dur * RATE);
        float[] out = new float[n];
        double b = 0.00002 * Math.pow(2, (midi - 48) / 9.0); // inharmonicity grows up the keyboard
        double rel = f / 261.63;
        for (int k = 1; k <= 14; k++) {
            // stretched like real strings, but with the fundamental exactly on pitch
            double fk = k * f * Math.sqrt((1 + b * k * k) / (1 + b));
            if (fk > 10000) break;
            // hammer struck about 1/8 along the string: the 8th partial nearly vanishes
            double amp = Math.pow(k, -1.25) * (0.15 + Math.abs(Math.sin(Math.PI * k / 8.3)));
            double fast = 2.2 + 0.9 * k * rel, slow = 0.35 + 0.12 * k * rel;
            for (int s = -1; s <= 1; s += 2) {
                double ff = fk * (1 + s * 0.0003); // two strings, slightly apart
                addPartial(out, ff, amp * 0.5, fast, slow);
            }
        }
        // the felt hammer: a soft, short thump of noise
        Random r = new Random(midi);
        double lp = 0;
        for (int i = 0; i < Math.min(n, RATE / 40); i++) {
            lp += 0.25 * ((r.nextDouble() * 2 - 1) - lp);
            out[i] += (float) (lp * 0.25 * Math.exp(-i / (RATE * 0.004)));
        }
        attack(out, 0.0015);
        return finish(out, 0.55);
    }

    /** One sine partial with a two-stage exponential decay, by a rotating phasor (fast, no Math.sin per sample). */
    private static void addPartial(float[] out, double hz, double amp, double fast, double slow) {
        double w = 2 * Math.PI * hz / RATE, c = Math.cos(w), s = Math.sin(w);
        double x = 1, y = 0;
        double ef = Math.exp(-fast / RATE), es = Math.exp(-slow / RATE), gf = 0.62 * amp, gs = 0.38 * amp;
        for (int i = 0; i < out.length; i++) {
            out[i] += (float) (y * (gf + gs));
            gf *= ef;
            gs *= es;
            double nx = x * c - y * s;
            y = x * s + y * c;
            x = nx;
            if ((i & 1023) == 0) { // keep the phasor on the unit circle
                double m = Math.sqrt(x * x + y * y);
                x /= m;
                y /= m;
            }
            if (gf + gs < 1e-5) break;
        }
    }

    // ---------------------------------------------------------------- guitar

    /**
     * A plucked steel string, Karplus-Strong style: a burst of noise (shaped by where the pick hits) circulates in a
     * delay line one period long and is gently low-passed each time round, which is exactly how a string loses its
     * high harmonics first. A fractional all-pass delay tunes it exactly, not just to the nearest sample.
     */
    static short[] guitar(int midi) {
        double f = freq(midi);
        double period = RATE / f;
        int delay = (int) Math.floor(period - 0.5 - 0.1);
        double frac = period - 0.5 - delay; // what the two-point average (0.5) and the integer delay leave over
        double ap = (1 - frac) / (1 + frac);
        double t60 = 6.5 - (midi - 40) * 0.11; // low strings ring longer
        double rho = Math.pow(0.001, 1.0 / (t60 * f));
        int n = (int) (Math.min(4.0, t60 * 0.8) * RATE);
        float[] out = new float[n];
        // excitation: noise, softened, with the pick position (a fifth of the way along) notched out
        Random r = new Random(midi * 31L);
        double[] exc = new double[delay];
        double lp = 0;
        for (int i = 0; i < delay; i++) {
            lp += 0.6 * ((r.nextDouble() * 2 - 1) - lp);
            exc[i] = lp;
        }
        int pick = Math.max(1, (int) Math.round(delay * 0.2));
        for (int i = delay - 1; i >= pick; i--) exc[i] -= exc[i - pick];
        double[] line = new double[delay];
        int idx = 0;
        double prev = 0, apIn = 0, apOut = 0, body = 0;
        for (int i = 0; i < n; i++) {
            double x = i < delay ? exc[i] : 0;
            double d = line[idx];
            double avg = 0.5 * (d + prev);
            prev = d;
            double a = ap * avg + apIn - ap * apOut;
            apIn = avg;
            apOut = a;
            double y = x + rho * a;
            line[idx] = y;
            idx = (idx + 1) % delay;
            body += 0.35 * (y - body); // the wooden body takes the edge off the very top
            out[i] = (float) (0.7 * y + 0.3 * body);
        }
        attack(out, 0.0008);
        return finish(out, 0.6);
    }

    /** Standard tuning, low to high: E2 A2 D3 G3 B3 E4. */
    static final int[] GUITAR_STRINGS = {40, 45, 50, 55, 59, 64};

    /** Chord shapes as frets per string, low E to high E; -1 is a string not played. */
    static final String[] CHORDS = {"C", "G", "D", "Am", "Em", "F"};
    static final int[][] FRETS = {
            {-1, 3, 2, 0, 1, 0},   // C
            {3, 2, 0, 0, 0, 3},    // G
            {-1, -1, 0, 2, 3, 2},  // D
            {-1, 0, 2, 2, 1, 0},   // Am
            {0, 2, 2, 0, 0, 0},    // Em
            {-1, -1, 3, 2, 1, 1},  // F (the easy four-string shape)
    };

    // ---------------------------------------------------------------- xylophone

    /**
     * A rosewood bar struck by a hard mallet: the fundamental, the third partial a real xylophone bar is carved to
     * (three times the fundamental, a twelfth above), and two untuned higher modes that die quickly, plus the click
     * of the mallet. The tube under each bar makes the fundamental ring a little longer.
     */
    static short[] xylophone(int midi) {
        double f = freq(midi);
        int n = (int) (1.3 * RATE);
        float[] out = new float[n];
        double[][] modes = {{1.0, 1.0, 3.2 + f / 900}, {3.0, 0.32, 9}, {6.24, 0.1, 18}, {9.5, 0.04, 30}};
        for (double[] m : modes) {
            if (m[0] * f > 16000) continue;
            addPartial(out, m[0] * f, m[1], m[2] * 1.6, m[2]);
        }
        Random r = new Random(midi * 7L);
        double hp = 0, last = 0;
        for (int i = 0; i < RATE / 60; i++) {
            double nz = r.nextDouble() * 2 - 1;
            hp = 0.7 * (hp + nz - last);
            last = nz;
            out[i] += (float) (hp * 0.35 * Math.exp(-i / (RATE * 0.0015)));
        }
        attack(out, 0.0005);
        return finish(out, 0.55);
    }

    // ---------------------------------------------------------------- drums

    static final String[] DRUMS = {"Kick", "Snare", "Hi-hat", "Open hat", "Tom 1", "Tom 2", "Floor tom", "Crash"};

    static short[] drum(int which) {
        switch (which) {
            case 0: return kick();
            case 1: return snare();
            case 2: return hat(0.09);
            case 3: return hat(0.45);
            case 4: return tom(196);
            case 5: return tom(147);
            case 6: return tom(98);
            default: return crash();
        }
    }

    /** A bass drum: a deep sine whose pitch drops fast as the head settles, with the beater's click. */
    private static short[] kick() {
        int n = (int) (0.7 * RATE);
        float[] out = new float[n];
        double phase = 0;
        Random r = new Random(1);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double hz = 48 + 120 * Math.exp(-t * 28);
            phase += 2 * Math.PI * hz / RATE;
            double v = Math.sin(phase) * Math.exp(-t * 6.5);
            if (t < 0.004) v += (r.nextDouble() * 2 - 1) * 0.5 * (1 - t / 0.004);
            out[i] = (float) v;
        }
        attack(out, 0.0004);
        return finish(out, 0.9);
    }

    /** A snare: the head's two lowest modes plus the rattle of the wires underneath (bright noise). */
    private static short[] snare() {
        int n = (int) (0.4 * RATE);
        float[] out = new float[n];
        addPartial(out, 185, 0.5, 30, 22);
        addPartial(out, 330, 0.3, 40, 30);
        Random r = new Random(2);
        double hp = 0, last = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, nz = r.nextDouble() * 2 - 1;
            hp = 0.92 * (hp + nz - last);
            last = nz;
            lp += 0.55 * (hp - lp);
            out[i] += (float) (lp * 0.9 * Math.exp(-t * 14));
        }
        attack(out, 0.0004);
        return finish(out, 0.75);
    }

    /** A hi-hat: six square waves at clashing, unrelated pitches (how metal plates ring) plus hiss, high-passed. */
    private static short[] hat(double decay) {
        int n = (int) ((decay * 2.5 + 0.05) * RATE);
        float[] out = new float[n];
        double[] hz = {205.3, 304.4, 369.6, 522.7, 540.0, 800.0};
        double[] ph = new double[hz.length];
        Random r = new Random(3);
        double hp1 = 0, l1 = 0, hp2 = 0, l2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, metal = 0;
            for (int k = 0; k < hz.length; k++) {
                ph[k] += hz[k] * 1.9 / RATE;
                metal += (ph[k] % 1.0) < 0.5 ? 1 : -1;
            }
            double v = metal / 6 * 0.6 + (r.nextDouble() * 2 - 1) * 0.5;
            hp1 = 0.85 * (hp1 + v - l1);
            l1 = v;
            hp2 = 0.85 * (hp2 + hp1 - l2);
            l2 = hp1;
            out[i] = (float) (hp2 * Math.exp(-t / decay * 3));
        }
        attack(out, 0.0003);
        return finish(out, 0.5);
    }

    /** A tom: a pitched head that bends down a little as it rings, with a touch of stick noise. */
    private static short[] tom(double hz) {
        int n = (int) (0.9 * RATE);
        float[] out = new float[n];
        double phase = 0, phase2 = 0;
        Random r = new Random((long) hz);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double f = hz * (1 + 0.45 * Math.exp(-t * 18));
            phase += 2 * Math.PI * f / RATE;
            phase2 += 2 * Math.PI * f * 1.59 / RATE; // the head's second mode
            double v = (Math.sin(phase) + 0.25 * Math.sin(phase2) * Math.exp(-t * 6)) * Math.exp(-t * 5);
            if (t < 0.01) v += (r.nextDouble() * 2 - 1) * 0.3 * (1 - t / 0.01);
            out[i] = (float) v;
        }
        attack(out, 0.0004);
        return finish(out, 0.8);
    }

    /** A crash cymbal: a wash of clashing metal tones and bright noise that blooms and fades over two seconds. */
    private static short[] crash() {
        int n = (int) (2.6 * RATE);
        float[] out = new float[n];
        double[] hz = {205.3, 304.4, 369.6, 522.7, 540.0, 800.0, 1050.0, 1350.0};
        double[] ph = new double[hz.length];
        Random r = new Random(4);
        double hp1 = 0, l1 = 0, hp2 = 0, l2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, metal = 0;
            for (int k = 0; k < hz.length; k++) {
                ph[k] += hz[k] * 2.6 / RATE;
                metal += (ph[k] % 1.0) < 0.5 ? 1 : -1;
            }
            double v = metal / 8 * 0.5 + (r.nextDouble() * 2 - 1) * 0.6;
            hp1 = 0.8 * (hp1 + v - l1);
            l1 = v;
            hp2 = 0.8 * (hp2 + hp1 - l2);
            l2 = hp1;
            double env = Math.min(1, t / 0.01) * Math.exp(-t * 1.5);
            out[i] = (float) (hp2 * env);
        }
        return finish(out, 0.55);
    }

    // ---------------------------------------------------------------- helpers

    private static void attack(float[] out, double seconds) {
        int a = (int) (seconds * RATE);
        for (int i = 0; i < a && i < out.length; i++) out[i] *= i / (float) a;
    }

    /** Scales to the given peak, fades the very end, and converts to 16-bit. */
    private static short[] finish(float[] out, double peak) {
        float max = 1e-6f;
        for (float v : out) max = Math.max(max, Math.abs(v));
        float g = (float) (peak / max);
        int fade = Math.min(out.length, RATE / 50);
        short[] pcm = new short[out.length];
        for (int i = 0; i < out.length; i++) {
            float v = out[i] * g;
            if (i > out.length - fade) v *= (out.length - i) / (float) fade;
            pcm[i] = (short) Math.max(-32767, Math.min(32767, Math.round(v * 32767)));
        }
        return pcm;
    }
}
