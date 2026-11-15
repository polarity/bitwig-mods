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
    private record Lane(int channel, int key) {}

    private IntervalEdit() {}

    static Edit plan(String label, List<Note> notes, int semitones, boolean duplicate) {
        Set<Note> selected = new LinkedHashSet<>(notes);
        List<Note> sources = new ArrayList<>(selected);
        if (!duplicate) {
            // Move the leading notes first so they vacate lanes that other selected notes need.
            Comparator<Note> byKey = Comparator.comparingInt(Note::key);
            sources.sort(semitones > 0 ? byKey.reversed() : byKey);
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
            Lane destination = new Lane(note.channel(), key);
            List<Note> lane = occupied.computeIfAbsent(destination, ignored -> new ArrayList<>());
            if (lane.stream().anyMatch(other -> note.start() < other.end() && other.start() < note.end())) {
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

    private static Map<Lane, List<Note>> occupiedLanes(Clip clip, Set<Note> moving) {
        Map<Lane, List<Note>> occupied = new HashMap<>();
        for (Note note : clip.notesBetween(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            if (!moving.contains(note)) {
                Lane lane = new Lane(note.channel(), note.key());
                occupied.computeIfAbsent(lane, ignored -> new ArrayList<>()).add(note);
            }
        }
        return occupied;
    }
}
