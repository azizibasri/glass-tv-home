package com.belajarceria.kids;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The music corner's mixer: one low-latency audio stream, many notes at once. Notes are rendered once by
 * {@link SoundBank} and cached; a key held down keeps sounding and fades when let go, like a damper falling.
 * Runs only while the music screen is open.
 */
final class Synth {
    private static final int FRAMES = 256;
    private static final int MAX_VOICES = 32;
    private static final Synth INSTANCE = new Synth();

    static final class Voice {
        final short[] data;
        final float gain;
        int pos, fadeLen, fadeLeft;

        Voice(short[] data, float gain) {
            this.data = data;
            this.gain = gain;
        }
    }

    private final ArrayList<Voice> voices = new ArrayList<>();
    private final Map<String, short[]> cache = new ConcurrentHashMap<>();
    private volatile boolean running;
    private AudioTrack track;
    private Thread thread;

    static Synth get() {
        return INSTANCE;
    }

    interface Render {
        short[] make();
    }

    /** A note's samples, made the first time they are needed and kept. */
    short[] sound(String key, Render r) {
        short[] s = cache.get(key);
        if (s == null) {
            s = r.make();
            cache.put(key, s);
        }
        return s;
    }

    /** Renders notes in the background so the first press of each is instant. */
    void prewarm(String[] keys, Render[] renders) {
        new Thread(() -> {
            for (int i = 0; i < keys.length; i++) sound(keys[i], renders[i]);
        }, "prewarm").start();
    }

    Voice play(short[] data, float gain) {
        Voice v = new Voice(data, gain);
        synchronized (voices) {
            if (voices.size() >= MAX_VOICES) voices.remove(0);
            voices.add(v);
        }
        return v;
    }

    /** Lets a held note go: it fades out over the given time instead of stopping dead. */
    void release(Voice v, float seconds) {
        if (v == null) return;
        synchronized (voices) {
            if (v.fadeLen == 0) {
                v.fadeLen = Math.max(1, (int) (seconds * SoundBank.RATE));
                v.fadeLeft = v.fadeLen;
            }
        }
    }

    synchronized void start() {
        if (running) return;
        int min = AudioTrack.getMinBufferSize(SoundBank.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SoundBank.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, FRAMES * 2 * 4))
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
        } catch (RuntimeException e) {
            track = null; // no audio output: the instruments still light up, silently
            return;
        }
        running = true;
        thread = new Thread(this::loop, "synth");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    synchronized void stop() {
        if (!running) return;
        running = false;
        try {
            thread.join(500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        track.release();
        track = null;
        synchronized (voices) {
            voices.clear();
        }
    }

    private void loop() {
        float[] mix = new float[FRAMES];
        short[] out = new short[FRAMES];
        AudioTrack t = track;
        while (running) {
            java.util.Arrays.fill(mix, 0f);
            synchronized (voices) {
                for (int v = voices.size() - 1; v >= 0; v--) {
                    Voice voice = voices.get(v);
                    boolean done = false;
                    for (int i = 0; i < FRAMES; i++) {
                        if (voice.pos >= voice.data.length) {
                            done = true;
                            break;
                        }
                        float s = voice.data[voice.pos++] * voice.gain;
                        if (voice.fadeLen > 0) {
                            s *= voice.fadeLeft / (float) voice.fadeLen;
                            if (--voice.fadeLeft <= 0) {
                                done = true;
                                break;
                            }
                        }
                        mix[i] += s;
                    }
                    if (done) voices.remove(v);
                }
            }
            for (int i = 0; i < FRAMES; i++) {
                // a soft limiter: many notes at once get rounder, never harshly clipped
                float x = mix[i] / 32768f * 0.8f;
                float y = 1.25f * x / (1f + 0.6f * Math.abs(x));
                out[i] = (short) Math.max(-32767, Math.min(32767, y * 32767f));
            }
            t.write(out, 0, FRAMES);
        }
    }
}
