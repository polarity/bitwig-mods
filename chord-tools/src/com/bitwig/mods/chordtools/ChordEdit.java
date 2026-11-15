package com.bitwig.mods.chordtools;

import bml.pianoroll.Clip;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ChordEdit {
    private static final Comparator<Note> BY_KEY = Comparator.comparingInt(Note::key);

    private record Lane(int channel, int key) {}
    private record Chord(Clip clip, int channel, double start) {}
    private record Move(Note note, int key) {}

    record QualityChord(List<Note> notes, List<Integer> roots) {}

    private ChordEdit() {}

    static Edit planInversions(String label, List<Note> notes, boolean up) {
        List<Move> moves = new ArrayList<>();
        for (List<Note> chord : chords(notes).values()) {
            if (chord.size() < 2) {
                continue;
            }
            Note source = up ? chord.getFirst() : chord.getLast();
            Note neighbor = up ? chord.get(1) : chord.get(chord.size() - 2);
            if (source.key() == neighbor.key()) {
                continue; // moving just one of coincident edge pitches would not rotate the chord
            }
            int opposite = (up ? chord.getLast() : chord.getFirst()).key();
            int octaves = Math.abs(opposite - source.key()) / 12 + 1;
            int key = source.key() + (up ? 12 : -12) * octaves;
            if (key >= 0 && key <= 127) {
                moves.add(new Move(source, key));
            }
        }

        // Vacate higher sources first when moving up, and lower sources first when moving down.
        moves.sort(Comparator.comparing(Move::note, up ? BY_KEY.reversed() : BY_KEY));
        Map<Clip, Map<Lane, List<Note>>> occupiedByClip = new HashMap<>();
        Edit edit = PianoRoll.edit(label);
        for (Move move : moves) {
            Note note = move.note();
            Map<Lane, List<Note>> occupied = occupiedByClip.computeIfAbsent(note.clip(), ChordEdit::occupiedLanes);
            List<Note> destination = laneAt(occupied, note.channel(), move.key());
            if (!overlaps(note, destination)) {
                moveNote(edit, occupied, note, move.key());
            }
        }
        return edit;
    }

    static Edit planVoicings(String label, List<Note> notes, boolean open, boolean keepBass) {
        Edit edit = PianoRoll.edit(label);
        Map<Clip, Map<Lane, List<Note>>> occupiedByClip = new HashMap<>();
        for (var entry : chords(notes).entrySet()) {
            Chord group = entry.getKey();
            List<Note> chord = entry.getValue();
            if (chord.size() < 3) {
                continue;
            }
            int[] pitches = chord.stream().mapToInt(Note::key).toArray();
            Set<Note> voices = Set.copyOf(chord);
            var occupied = occupiedByClip.computeIfAbsent(group.clip(), ChordEdit::occupiedLanes);
            int[] targets = Voicing.choose(pitches, open, keepBass,
                    keys -> destinationsAreClear(chord, voices, occupied, keys));
            if (targets == null) {
                continue;
            }
            // Within each pitch class, the new notes preserve order. Move up from the top and down from the bottom.
            for (int i = pitches.length - 1; i >= 0; i--) {
                if (targets[i] > pitches[i]) {
                    moveNote(edit, occupied, chord.get(i), targets[i]);
                }
            }
            for (int i = 0; i < pitches.length; i++) {
                if (targets[i] < pitches[i]) {
                    moveNote(edit, occupied, chord.get(i), targets[i]);
                }
            }
        }
        return edit;
    }

    static List<QualityChord> qualityChords(List<Note> selection) {
        List<QualityChord> result = new ArrayList<>();
        for (List<Note> chord : chords(selection).values()) {
            List<Integer> roots = ChordQuality.roots(chord.stream().mapToInt(Note::key).toArray());
            if (!roots.isEmpty()) {
                result.add(new QualityChord(chord, roots));
            }
        }
        return result;
    }

    static Edit planQuality(String label, List<QualityChord> chords, int[] roots, ChordQuality quality) {
        Edit edit = PianoRoll.edit(label);
        Map<Clip, Map<Lane, List<Note>>> occupiedByClip = new HashMap<>();
        boolean convertible = false;
        for (int group = 0; group < chords.size(); group++) {
            List<Note> chord = chords.get(group).notes();
            var occupied = occupiedByClip.computeIfAbsent(chord.getFirst().clip(), ChordEdit::occupiedLanes);
            int[] pitches = chord.stream().mapToInt(Note::key).toArray();
            int[] targets = quality.convert(pitches, roots[group], (voice, key) -> {
                Note note = chord.get(voice);
                return !overlaps(note, laneAt(occupied, note.channel(), key));
            });
            if (targets == null) {
                continue;
            }
            convertible = true;
            for (int i = 0; i < pitches.length; i++) {
                if (targets[i] != pitches[i]) {
                    moveNote(edit, occupied, chord.get(i), targets[i]);
                }
            }
        }
        if (!convertible) {
            throw new Refused("no selected chord has a safe conversion");
        }
        return edit;
    }

    private static Map<Chord, List<Note>> chords(List<Note> notes) {
        Map<Chord, List<Note>> chords = new LinkedHashMap<>();
        for (Note note : new LinkedHashSet<>(notes)) {
            Clip clip = note.clip();
            if (clip != null) {
                Chord chord = new Chord(clip, note.channel(), note.start());
                chords.computeIfAbsent(chord, ignored -> new ArrayList<>()).add(note);
            }
        }
        chords.values().forEach(chord -> chord.sort(BY_KEY));
        return chords;
    }

    private static boolean destinationsAreClear(List<Note> chord, Set<Note> voices,
                                                 Map<Lane, List<Note>> occupied, int[] keys) {
        for (int i = 0; i < keys.length; i++) {
            Note note = chord.get(i);
            if (keys[i] != note.key()) {
                for (Note other : laneAt(occupied, note.channel(), keys[i])) {
                    if (!voices.contains(other) && overlaps(note, other)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void moveNote(Edit edit, Map<Lane, List<Note>> occupied, Note note, int key) {
        edit.setKey(note, key);
        laneAt(occupied, note.channel(), note.key()).remove(note);
        laneAt(occupied, note.channel(), key).add(note);
    }

    private static boolean overlaps(Note note, List<Note> lane) {
        return lane.stream().anyMatch(other -> overlaps(note, other));
    }

    private static boolean overlaps(Note note, Note other) {
        return note.start() < other.end() && other.start() < note.end();
    }

    private static List<Note> laneAt(Map<Lane, List<Note>> occupied, int channel, int key) {
        return occupied.computeIfAbsent(new Lane(channel, key), ignored -> new ArrayList<>());
    }

    private static Map<Lane, List<Note>> occupiedLanes(Clip clip) {
        Map<Lane, List<Note>> occupied = new HashMap<>();
        for (Note note : clip.notesBetween(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            laneAt(occupied, note.channel(), note.key()).add(note);
        }
        return occupied;
    }
}
