package com.bitwig.mods.chordtools;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;

final class Voicing {
    private static final int OCTAVE = 12;
    private static final int MAX_KEY = 127;
    private static final Comparator<Layout> OPEN_ORDER = Comparator.comparingInt(Layout::span);
    private static final Comparator<Layout> CLOSE_ORDER = Comparator.comparingInt(Layout::travel)
            .thenComparingInt(Layout::centerShift)
            .thenComparingInt(Layout::movedVoices)
            .thenComparing(Comparator.comparingInt(Layout::span).reversed());

    private record Layout(int[] keys, int span, int travel, int centerShift, int movedVoices) {}

    private Voicing() {}

    /** Input pitches are sorted; output keys follow the same note order. */
    static int[] choose(int[] pitches, boolean open, boolean keepBass, Predicate<int[]> available) {
        for (int i = 1; i < pitches.length; i++) {
            if (pitches[i] == pitches[i - 1]) {
                return null;
            }
        }
        Layout best = open ? open(pitches, keepBass, available) : close(pitches, keepBass, available);
        return best == null ? null : best.keys();
    }

    private static Layout open(int[] pitches, boolean keepBass, Predicate<int[]> available) {
        int bass = pitches[0];
        int top = pitches[pitches.length - 1];
        int minimumSpan = Math.max(OCTAVE, top - bass + 1);
        Layout best = null;
        for (int i = keepBass ? 1 : 0; i < pitches.length; i++) {
            int otherLow = i == 0 ? pitches[1] : bass;
            int otherHigh = i == pitches.length - 1 ? pitches[i - 1] : top;
            for (int shift = -OCTAVE; shift <= OCTAVE; shift += 2 * OCTAVE) {
                int key = pitches[i] + shift;
                if (key < (keepBass ? bass : 0) || key > MAX_KEY || Arrays.binarySearch(pitches, key) >= 0) {
                    continue;
                }
                int span = Math.max(otherHigh, key) - Math.min(otherLow, key);
                if (span >= minimumSpan) {
                    int[] keys = pitches.clone();
                    keys[i] = key;
                    best = prefer(best, new Layout(keys, span, OCTAVE, OCTAVE, 1), OPEN_ORDER, available);
                }
            }
        }
        return best;
    }

    private static Layout close(int[] pitches, boolean keepBass, Predicate<int[]> available) {
        int[] counts = new int[OCTAVE];
        for (int pitch : pitches) {
            counts[pitch % OCTAVE]++;
        }
        int currentSpan = pitches[pitches.length - 1] - pitches[0];
        int firstBass = keepBass ? pitches[0] : 0;
        int lastBass = keepBass ? pitches[0] : MAX_KEY;
        Layout best = null;
        for (int bass = firstBass; bass <= lastBass; bass++) {
            if (counts[bass % OCTAVE] == 0) {
                continue;
            }
            int requiredTop = bass;
            for (int pitchClass = 0; pitchClass < OCTAVE; pitchClass++) {
                if (counts[pitchClass] > 0) {
                    int lowest = bass + Math.floorMod(pitchClass - bass, OCTAVE);
                    requiredTop = Math.max(requiredTop, lowest + OCTAVE * (counts[pitchClass] - 1));
                }
            }
            // Use the widest range that still closes the chord. An already nearby voice can keep its octave.
            int top = Math.min(MAX_KEY, bass + currentSpan - 1);
            if (requiredTop <= top) {
                best = prefer(best, pack(pitches, counts, bass, top), CLOSE_ORDER, available);
            }
        }
        return best;
    }

    private static Layout pack(int[] pitches, int[] counts, int bass, int top) {
        int[] keys = new int[pitches.length];
        int[] ranks = new int[OCTAVE];
        int travel = 0;
        int centerShift = 0;
        int moved = 0;
        int highest = bass;
        for (int i = 0; i < pitches.length; i++) {
            int pitch = pitches[i];
            int pitchClass = pitch % OCTAVE;
            int rank = ranks[pitchClass]++;
            // Reserve a separate octave slot for each doubling of this pitch class.
            int low = bass + Math.floorMod(pitch - bass, OCTAVE) + rank * OCTAVE;
            int high = top - Math.floorMod(top - pitch, OCTAVE) - (counts[pitchClass] - rank - 1) * OCTAVE;
            int key = low == bass ? bass : Math.clamp(pitch, low, high);
            keys[i] = key;
            highest = Math.max(highest, key);
            int shift = key - pitch;
            travel += Math.abs(shift);
            centerShift += shift;
            if (shift != 0) {
                moved++;
            }
        }
        return new Layout(keys, highest - bass, travel, Math.abs(centerShift), moved);
    }

    private static Layout prefer(Layout best, Layout candidate, Comparator<Layout> order, Predicate<int[]> available) {
        return (best == null || order.compare(candidate, best) < 0) && available.test(candidate.keys())
                ? candidate : best;
    }
}
