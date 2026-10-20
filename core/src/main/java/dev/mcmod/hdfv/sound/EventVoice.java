package dev.mcmod.hdfv.sound;

import dev.mcmod.aerocore.api.acoustics.Voice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Short sounds that happen (0.19): a door, a gear's clunk, an impact, a hiss of air, played into a vehicle's mix as
 * they come, overlapping. Each comes with its sound power level; while it plays, {@link #levelDb} is the loudest one's,
 * so the vehicle's balance gives the events their share (a door slam beside an idling engine is heard). The samples
 * are at {@link Voice#RATE}, about full scale; they are mixed relative to the loudest.
 */
final class EventVoice implements Voice {
    private static final class Event {
        final float[] samples;
        final double levelDb;
        int at;
        Event(float[] samples, double levelDb) { this.samples = samples; this.levelDb = levelDb; }
    }

    private final ConcurrentLinkedQueue<Event> queued = new ConcurrentLinkedQueue<>();
    private final List<Event> playing = new ArrayList<>();
    /** Game thread: what was started and until when (nanoTime), for the level. */
    private final List<double[]> heard = new ArrayList<>();

    /** Starts a sound (any thread). */
    void play(float[] samples, double levelDb) {
        if (samples == null || samples.length == 0 || !(levelDb > -90)) { return; }
        queued.add(new Event(samples, levelDb));
        synchronized (heard) { heard.add(new double[]{System.nanoTime() + samples.length * 1e9 / RATE, levelDb}); }
    }

    /** The loudest event playing now, dB of sound power (-100 when none). */
    double levelDb() {
        double now = System.nanoTime(), level = -100;
        synchronized (heard) {
            for (Iterator<double[]> it = heard.iterator(); it.hasNext(); ) {
                double[] h = it.next();
                if (h[0] < now) { it.remove(); continue; }
                level = Math.max(level, h[1]);
            }
        }
        return level;
    }

    @Override public void render(float[] out, int offset, int n) {
        java.util.Arrays.fill(out, offset, offset + n, 0f);
        for (Event e; (e = queued.poll()) != null; ) { playing.add(e); }
        if (playing.isEmpty()) { return; }
        double top = -100;
        for (Event e : playing) { top = Math.max(top, e.levelDb); }
        for (Iterator<Event> it = playing.iterator(); it.hasNext(); ) {
            Event e = it.next();
            float g = (float) Math.pow(10, (e.levelDb - top) / 20);
            int k = Math.min(n, e.samples.length - e.at);
            for (int i = 0; i < k; i++) { out[offset + i] += e.samples[e.at + i] * g; }
            e.at += k;
            if (e.at >= e.samples.length) { it.remove(); }
        }
    }
}
