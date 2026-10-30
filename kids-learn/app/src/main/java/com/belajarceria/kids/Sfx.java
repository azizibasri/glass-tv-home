package com.belajarceria.kids;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Soft bell-like sound effects, synthesised once at start-up: no audio files in the APK.
 * Each one is a short sequence of notes with a quick attack and a gentle decay.
 */
final class Sfx {
    private static final int RATE = 22050;
    private static volatile AudioTrack pop, tick, ding, oops, fanfare, zap;

    private Sfx() {}

    static void init() {
        new Thread(() -> {
            pop = track(render(0.12f, new float[][]{{0f, 1046.5f, 38f, 0.30f}}));
            tick = track(render(0.08f, new float[][]{{0f, 784f, 55f, 0.22f}}));
            ding = track(render(0.7f, new float[][]{
                    {0f, 1046.5f, 7f, 0.26f}, {0.08f, 1318.5f, 7f, 0.26f}, {0.16f, 1568f, 6f, 0.28f}}));
            oops = track(render(0.5f, new float[][]{{0f, 392f, 12f, 0.30f}, {0.13f, 311f, 9f, 0.30f}}));
            fanfare = track(render(1.4f, new float[][]{
                    {0f, 523.3f, 6f, 0.22f}, {0.11f, 659.3f, 6f, 0.22f}, {0.22f, 784f, 6f, 0.22f},
                    {0.33f, 1046.5f, 3.2f, 0.26f}, {0.33f, 784f, 3.2f, 0.16f}, {0.33f, 659.3f, 3.2f, 0.12f}}));
            zap = track(sweep(0.32f, 1800f, 420f, 0.22f));
        }, "sfx").start();
    }

    static void pop() { play(pop); }
    static void tick() { play(tick); }
    static void ding() { play(ding); }
    static void oops() { play(oops); }
    static void fanfare() { play(fanfare); }
    static void zap() { play(zap); }

    static void release() {
        for (AudioTrack t : new AudioTrack[]{pop, tick, ding, oops, fanfare, zap}) if (t != null) t.release();
        pop = tick = ding = oops = fanfare = zap = null;
    }

    /** @param notes rows of {start seconds, frequency Hz, decay per second, volume} */
    private static short[] render(float seconds, float[][] notes) {
        int n = (int) (seconds * RATE);
        float[] mix = new float[n];
        for (float[] note : notes) {
            int start = (int) (note[0] * RATE);
            double w = 2 * Math.PI * note[1] / RATE;
            for (int i = start; i < n; i++) {
                float t = (i - start) / (float) RATE;
                float attack = Math.min(1f, t / 0.006f);
                double env = attack * Math.exp(-note[2] * t);
                if (env < 0.0005 && t > 0.05f) break;
                int k = i - start;
                // A bell: the fundamental plus two quieter overtones that fade faster.
                double s = Math.sin(w * k) + 0.3 * Math.sin(2 * w * k) * Math.exp(-note[2] * t)
                        + 0.1 * Math.sin(3 * w * k);
                mix[i] += (float) (s * env * note[3]);
            }
        }
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            float fadeOut = Math.min(1f, (n - i) / (0.02f * RATE));
            float v = Math.max(-1f, Math.min(1f, mix[i] * fadeOut));
            pcm[i] = (short) (v * 32000);
        }
        return pcm;
    }

    /** A falling "pew": a tone sliding from f0 down to f1, with a little shimmer, for the heroes' laser. */
    private static short[] sweep(float seconds, float f0, float f1, float vol) {
        int n = (int) (seconds * RATE);
        short[] pcm = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = f0 * Math.pow(f1 / f0, t);
            phase += 2 * Math.PI * f / RATE;
            float env = Math.min(1f, i / (0.004f * RATE)) * (1f - t) * (1f - t);
            double s = Math.sin(phase) * 0.8 + Math.sin(phase * 2.01) * 0.2;
            pcm[i] = (short) (s * env * vol * 32000);
        }
        return pcm;
    }

    private static AudioTrack track(short[] pcm) {
        try {
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(pcm, 0, pcm.length);
            return t;
        } catch (RuntimeException e) {
            return null; // no audio device: the app simply stays quiet
        }
    }

    private static void play(AudioTrack t) {
        if (t == null) return;
        try {
            t.stop();
            t.reloadStaticData();
            t.play();
        } catch (IllegalStateException ignored) {
            // released while the activity closes
        }
    }
}
