package com.bitwig.mods.chordtools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

enum ChordQuality {
    MINOR(3, 7), MAJOR(4, 7), DIMINISHED(3, 6), SUS2(2, 7), SUS4(5, 7);

    private static final int OCTAVE = 12;
    private static final int MAX_KEY = 127;
    private static final int THIRDS = mask(3, 4);
    private static final int SUSPENSIONS = mask(2, 5);
    private static final int SEVENTHS = mask(10, 11);

    // Equal matches can have different roots, for example C6 and Am7 or the rotations of dim7.
    private static final Set<Integer> SHAPES = Set.of(
            mask(0, 4, 7), mask(0, 3, 7), mask(0, 3, 6), mask(0, 4, 8), mask(0, 2, 7), mask(0, 5, 7),
            mask(0, 4, 7, 9), mask(0, 3, 7, 9), mask(0, 4, 7, 10), mask(0, 4, 7, 11),
            mask(0, 3, 7, 10), mask(0, 3, 7, 11), mask(0, 3, 6, 9), mask(0, 3, 6, 10),
            mask(0, 4, 8, 10), mask(0, 4, 8, 11), mask(0, 5, 7, 10), mask(0, 2, 7, 10),
            mask(0, 4, 10), mask(0, 4, 11), mask(0, 3, 10), mask(0, 3, 11),
            mask(0, 2, 4, 7), mask(0, 2, 3, 7), mask(0, 4, 5, 7),
            mask(0, 2, 4, 7, 10), mask(0, 2, 4, 7, 11), mask(0, 2, 3, 7, 10), mask(0, 2, 3, 7, 11),
            mask(0, 2, 4, 7, 9), mask(0, 2, 3, 7, 9), mask(0, 2, 3, 5, 7, 10),
            mask(0, 2, 4, 5, 7, 10), mask(0, 2, 4, 7, 9, 10), mask(0, 2, 4, 7, 9, 11),
            mask(0, 2, 3, 7, 9, 10), mask(0, 2, 4, 5, 7, 9, 10), mask(0, 2, 4, 6, 7, 11),
            mask(0, 4, 6, 7, 10), mask(0, 4, 6, 10), mask(0, 1, 4, 7, 10),
            mask(0, 3, 4, 7), mask(0, 3, 4, 7, 10));

    private final int third;
    private final int fifth;

    ChordQuality(int third, int fifth) {
        this.third = third;
        this.fifth = fifth;
    }

    static List<Integer> roots(int[] pitches) {
        int classes = relativeClasses(pitches, 0);
        if (Integer.bitCount(classes) < 3) {
            return List.of();
        }
        List<Integer> roots = new ArrayList<>();
        int strongest = -1;
        for (int root = 0; root < OCTAVE; root++) {
            if (!has(classes, root)) {
                continue;
            }
            int relative = relativeClasses(pitches, root);
            int support = rootSupport(relative);
            if (support > strongest) {
                strongest = support;
                roots.clear();
            }
            if (support == strongest) {
                roots.add(root);
            }
        }
        return List.copyOf(roots);
    }

    private static int rootSupport(int classes) {
        if (SHAPES.contains(classes)) {
            return 3;
        }
        boolean hasThird = (classes & THIRDS) != 0;
        if ((hasThird || (classes & SUSPENSIONS) != 0) && fifthRoles(classes) != 0) {
            return 2;
        }
        return hasThird && (classes & SEVENTHS) != 0 ? 1 : 0;
    }

    int[] convert(int[] pitches, int root, BiPredicate<Integer, Integer> canMove) {
        boolean[] occupied = new boolean[MAX_KEY + 1];
        for (int pitch : pitches) {
            if (occupied[pitch]) {
                return null;
            }
            occupied[pitch] = true;
        }

        int classes = relativeClasses(pitches, root);
        int thirdRoles = (classes & THIRDS) != 0 ? THIRDS : SUSPENSIONS;
        int fifthRoles = fifthRoles(classes);
        boolean shell = this != DIMINISHED && fifthRoles == 0 && (classes & THIRDS) != 0
                && (classes & SEVENTHS) != 0;
        int core = shell ? mask(0, third) : mask(0, third, fifth);
        int[] keys = pitches.clone();
        for (int i = 0; i < pitches.length; i++) {
            int interval = relative(pitches[i], root);
            if (has(thirdRoles | fifthRoles, interval) && !has(core, interval)) {
                keys[i] = -1;
            }
        }

        if (!fill(pitches, keys, occupied, root, third, thirdRoles, core, canMove)
                || (!shell && !fill(pitches, keys, occupied, root, fifth, fifthRoles, core, canMove))) {
            return null;
        }
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == -1) {
                // A split third or doubled voice can use another nearby core tone if its first choice is occupied.
                int key = nearestKey(pitches[i], i, root, core, occupied, canMove);
                if (key == -1) {
                    return null;
                }
                keys[i] = key;
                occupied[key] = true;
            }
        }
        return keys;
    }

    private static boolean fill(int[] pitches, int[] keys, boolean[] occupied, int root, int interval,
                                int role, int core, BiPredicate<Integer, Integer> canMove) {
        for (int key : keys) {
            if (key >= 0 && relative(key, root) == interval) {
                return true;
            }
        }
        int bestVoice = -1;
        int bestKey = -1;
        int bestCost = Integer.MAX_VALUE;
        for (int i = 0; i < keys.length; i++) {
            int source = relative(pitches[i], root);
            if (has(core, source)) {
                continue; // the root and already correct voices are fixed
            }
            if (keys[i] >= 0 && keys[i] != pitches[i]) {
                continue; // this voice already supplies another required tone
            }
            int key = nearestKey(pitches[i], i, root, mask(interval), occupied, canMove);
            if (key < 0) {
                continue;
            }
            // Prefer a voice already changing, then a voice in the corresponding harmonic role.
            int cost = (keys[i] >= 0 ? 2 * OCTAVE : 0) + (has(role, source) ? 0 : OCTAVE)
                    + Math.abs(key - pitches[i]);
            if (cost < bestCost) {
                bestVoice = i;
                bestKey = key;
                bestCost = cost;
            }
        }
        if (bestVoice < 0) {
            return false;
        }
        keys[bestVoice] = bestKey;
        occupied[bestKey] = true;
        return true;
    }

    private static int nearestKey(int pitch, int index, int root, int tones, boolean[] occupied,
                                  BiPredicate<Integer, Integer> canMove) {
        for (int distance = 1; distance <= OCTAVE; distance++) {
            int below = pitch - distance;
            if (below >= 0 && has(tones, relative(below, root)) && !occupied[below] && canMove.test(index, below)) {
                return below;
            }
            int above = pitch + distance;
            if (above <= MAX_KEY && has(tones, relative(above, root)) && !occupied[above] && canMove.test(index, above)) {
                return above;
            }
        }
        return -1;
    }

    private static int fifthRoles(int classes) {
        return has(classes, 7) ? mask(7) : classes & mask(6, 8);
    }

    private static int relativeClasses(int[] pitches, int root) {
        return Arrays.stream(pitches).map(pitch -> 1 << relative(pitch, root)).reduce(0, (a, b) -> a | b);
    }

    private static int relative(int pitch, int root) {
        return Math.floorMod(pitch - root, OCTAVE);
    }

    private static boolean has(int mask, int interval) {
        return (mask & (1 << interval)) != 0;
    }

    private static int mask(int... intervals) {
        int mask = 0;
        for (int interval : intervals) {
            mask |= 1 << interval;
        }
        return mask;
    }
}
