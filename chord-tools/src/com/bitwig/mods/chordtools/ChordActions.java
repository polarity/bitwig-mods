package com.bitwig.mods.chordtools;

import bitwig.obf.aMh; // Detail Editor widget
import bitwig.obf.bii; // Detail Editor model
import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.notifications.Notifications;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.NoteSelection;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ChordActions implements ActionProvider {
    // Keep the IDs from the combined Note Intervals build so Bitwig retains assigned shortcuts.
    private static final String ID_PREFIX = "note_intervals_";
    private static final String INVERSIONS = ID_PREFIX + "inversions";
    private static final String VOICINGS = ID_PREFIX + "voicings";
    private static final String CHORD_QUALITIES = ID_PREFIX + "chord_qualities";

    @Override
    public void declare(Actions actions) {
        actions.category(INVERSIONS, "Notes: Chord Inversions");
        actions.category(VOICINGS, "Notes: Chord Voicings");
        actions.category(CHORD_QUALITIES, "Notes: Chord Quality");
        addInversion(actions, "next_inversion", "Next Inversion", true);
        addInversion(actions, "previous_inversion", "Previous Inversion", false);
        addVoicing(actions, "open_voicing", "Open Voicing", true, false);
        addVoicing(actions, "close_voicing", "Close Voicing", false, false);
        addVoicing(actions, "open_voicing_keep_bass", "Open Voicing (Keep Bass)", true, true);
        addVoicing(actions, "close_voicing_keep_bass", "Close Voicing (Keep Bass)", false, true);
        addQuality(actions, "make_minor", "Make Minor", ChordQuality.MINOR);
        addQuality(actions, "make_major", "Make Major", ChordQuality.MAJOR);
        addQuality(actions, "make_diminished", "Make Diminished", ChordQuality.DIMINISHED);
        addQuality(actions, "make_sus2", "Make Sus2", ChordQuality.SUS2);
        addQuality(actions, "make_sus4", "Make Sus4", ChordQuality.SUS4);
    }

    private static void addInversion(Actions actions, String id, String label, boolean up) {
        addEdit(actions, id, label, INVERSIONS, notes -> ChordEdit.planInversions(label, notes, up));
    }

    private static void addVoicing(Actions actions, String id, String label, boolean open, boolean keepBass) {
        addEdit(actions, id, label, VOICINGS, notes -> ChordEdit.planVoicings(label, notes, open, keepBass));
    }

    private static void addQuality(Actions actions, String id, String label, ChordQuality quality) {
        actions.action(ID_PREFIX + id, label).category(CHORD_QUALITIES).bindOn(aMh.class)
                .onRun(context -> {
                    aMh widget = (aMh) context.widget();
                    try {
                        List<Note> selection = selectedNotes(widget);
                        var chords = ChordEdit.qualityChords(selection);
                        if (!chords.isEmpty()) {
                            resolveRoots(widget, label, quality, selection, chords, new int[chords.size()], 0);
                        }
                    } catch (Refused refused) {
                        Notifications.toast(label + ": " + refused.getMessage());
                    }
                });
    }

    private static void resolveRoots(aMh widget, String label, ChordQuality quality, List<Note> selection,
                                     List<ChordEdit.QualityChord> chords, int[] roots, int start) {
        try {
            if (start > 0 && !sameSelection(selection, selectedNotes(widget))) {
                throw new Refused("the note selection changed while choosing roots");
            }
            for (int i = start; i < chords.size(); i++) {
                var chord = chords.get(i);
                if (chord.roots().size() == 1) {
                    roots[i] = chord.roots().getFirst();
                    continue;
                }
                int index = i;
                String names = chord.notes().stream().map(note -> ChordRootDialog.pitchName(note.key())).distinct()
                        .collect(Collectors.joining(" – "));
                String summary = chords.size() == 1 ? names : "Chord " + (i + 1) + " of " + chords.size() + "   " + names;
                new ChordRootDialog(label, summary, chord.roots(), root -> {
                    roots[index] = root;
                    // Let the first modal finish closing before showing the next, or applying the edit.
                    PianoRoll.later(() -> resolveRoots(widget, label, quality, selection, chords, roots, index + 1));
                }).FzB(widget);
                return;
            }
            ChordEdit.planQuality(label, chords, roots, quality).apply();
        } catch (Refused refused) {
            Notifications.toast(label + ": " + refused.getMessage());
        }
    }

    private static boolean sameSelection(List<Note> before, List<Note> after) {
        var previous = new HashMap<Note, Note>();
        before.forEach(note -> previous.put(note, note));
        for (Note note : new HashSet<>(after)) {
            Note old = previous.remove(note);
            if (old == null || old.key() != note.key() || old.channel() != note.channel()
                    || old.start() != note.start() || old.end() != note.end() || !Objects.equals(old.clip(), note.clip())) {
                return false;
            }
        }
        return previous.isEmpty();
    }

    private static void addEdit(Actions actions, String id, String label, String category, Function<List<Note>, Edit> plan) {
        actions.action(ID_PREFIX + id, label)
                .category(category)
                .bindOn(aMh.class)
                .onRun(context -> run((aMh) context.widget(), label, plan));
    }

    private static void run(aMh widget, String label, Function<List<Note>, Edit> plan) {
        try {
            List<Note> notes = selectedNotes(widget);
            if (!notes.isEmpty()) {
                plan.apply(notes).apply();
            }
        } catch (Refused refused) {
            Notifications.toast(label + ": " + refused.getMessage());
        }
    }

    private static List<Note> selectedNotes(aMh widget) {
        bii editor = widget.Ktv(); // model of the editor holding the action binding
        if (editor == null) {
            return List.of();
        }
        // Read the active selection of this editor, including the time selection if that tool is active.
        NoteSelection selection = PianoRoll.selection(editor.IwV());
        return selection.hasOtherItems() ? List.of() : selection.notes();
    }
}
