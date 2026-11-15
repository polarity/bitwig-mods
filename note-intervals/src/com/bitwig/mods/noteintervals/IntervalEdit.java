package com.bitwig.mods.noteintervals;

import bml.pianoroll.Clip;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class IntervalEdit {
    private static final Comparator<Note> BY_KEY = Comparator.comparingInt(Note::key);

    private record Lane(int channel, int key) {}

    private IntervalEdit() {}

    static Edit plan(String label, List<Note> notes, int semitones, boolean duplicate) {
        Set<Note> selected = new LinkedHashSet<>(notes);
        List<Note> sources = new ArrayList<>(selected);
        if (!duplicate) {
            // Move the leading notes first so they vacate lanes that other selected notes need.
            sources.sort(semitones > 0 ? BY_KEY.reversed() : BY_KEY);
        }

        Set<Note> moving = duplicate ? Set.of() : selected;
        Map<Clip, Map<Lane, List<Note>>> occupiedByClip = new HashMap<>();
        Edit edit = PianoRoll.edit(label);
        for (Note note : sources) {
            int key = note.key() + semitones;
            if (key < 0 || key > 127) {
                if (duplicate) {
                    continue;
                }
                throw new Refused("the selection would move outside the MIDI note range");
            }
            Clip clip = note.clip();
            if (clip == null) {
                throw new Refused("a selected note has no editable clip");
            }

            Map<Lane, List<Note>> occupied = occupiedByClip.computeIfAbsent(clip, c -> occupiedLanes(c, moving));
            List<Note> lane = laneAt(occupied, note.channel(), key);
            if (overlaps(note, lane)) {
                if (duplicate) {
                    continue;
                }
                throw new Refused("the selection would overlap another note on the same pitch and MIDI channel");
            }

            if (duplicate) {
                edit.copy(note, key, note.start(), note.end());
            } else {
                edit.setKey(note, key);
            }
            // Occupancy uses the source's time span in the destination lane.
            lane.add(note);
        }
        return edit;
    }

    private static boolean overlaps(Note note, List<Note> lane) {
        return lane.stream().anyMatch(other -> note.start() < other.end() && other.start() < note.end());
    }

    private static List<Note> laneAt(Map<Lane, List<Note>> occupied, int channel, int key) {
        return occupied.computeIfAbsent(new Lane(channel, key), ignored -> new ArrayList<>());
    }

    private static Map<Lane, List<Note>> occupiedLanes(Clip clip, Set<Note> moving) {
        Map<Lane, List<Note>> occupied = new HashMap<>();
        for (Note note : clip.notesBetween(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            if (!moving.contains(note)) {
                laneAt(occupied, note.channel(), note.key()).add(note);
            }
        }
        return occupied;
    }
}
