package com.bitwig.mods.noteintervals;

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
import java.util.List;
import java.util.function.Function;

public final class IntervalActions implements ActionProvider {
    private static final String ID_PREFIX = "note_intervals_";
    private static final String DUPLICATION = ID_PREFIX + "duplication";
    private static final String TRANSPOSITION = ID_PREFIX + "transposition";

    @Override
    public void declare(Actions actions) {
        actions.category(DUPLICATION, "Notes: Duplicate by Interval");
        actions.category(TRANSPOSITION, "Notes: Transpose by Interval");
        add(actions, "transpose_fifth_up", "Transpose a Fifth Up", 7, false);
        add(actions, "transpose_fifth_down", "Transpose a Fifth Down", -7, false);
        add(actions, "duplicate_fifth_up", "Duplicate a Fifth Up", 7, true);
        add(actions, "duplicate_fifth_down", "Duplicate a Fifth Down", -7, true);
        add(actions, "duplicate_octave_up", "Duplicate an Octave Up", 12, true);
        add(actions, "duplicate_octave_down", "Duplicate an Octave Down", -12, true);
        addPair(actions, "minor_second", "a Minor Second", 1, true);
        addIntervalPairs(actions, "major_second", "a Major Second", 2);
        addIntervalPairs(actions, "minor_third", "a Minor Third", 3);
        addIntervalPairs(actions, "major_third", "a Major Third", 4);
        addIntervalPairs(actions, "fourth", "a Fourth", 5);
        addIntervalPairs(actions, "tritone", "a Tritone", 6);
        addIntervalPairs(actions, "minor_sixth", "a Minor Sixth", 8);
        addIntervalPairs(actions, "major_sixth", "a Major Sixth", 9);
        addIntervalPairs(actions, "minor_seventh", "a Minor Seventh", 10);
        addIntervalPairs(actions, "major_seventh", "a Major Seventh", 11);
        addPrompt(actions, "duplicate_by_interval", "Duplicate by Interval", true);
        addPrompt(actions, "transpose_by_interval", "Transpose by Interval", false);
    }

    private static void addIntervalPairs(Actions actions, String intervalId, String intervalName, int semitones) {
        addPair(actions, intervalId, intervalName, semitones, true);
        addPair(actions, intervalId, intervalName, semitones, false);
    }

    private static void addPair(Actions actions, String intervalId, String intervalName, int semitones, boolean duplicate) {
        String id = (duplicate ? "duplicate_" : "transpose_") + intervalId;
        String label = (duplicate ? "Duplicate " : "Transpose ") + intervalName;
        add(actions, id + "_up", label + " Up (+" + semitones + ")", semitones, duplicate);
        add(actions, id + "_down", label + " Down (-" + semitones + ")", -semitones, duplicate);
    }

    private static void add(Actions actions, String id, String label, int semitones, boolean duplicate) {
        addEdit(actions, id, label, duplicate ? DUPLICATION : TRANSPOSITION,
                notes -> IntervalEdit.plan(label, notes, semitones, duplicate));
    }

    private static void addEdit(Actions actions, String id, String label, String category, Function<List<Note>, Edit> plan) {
        actions.action(ID_PREFIX + id, label)
                .category(category)
                .bindOn(aMh.class)
                .onRun(context -> run((aMh) context.widget(), label, plan));
    }

    private static void addPrompt(Actions actions, String id, String label, boolean duplicate) {
        actions.action(ID_PREFIX + id, label + "…")
                .category(duplicate ? DUPLICATION : TRANSPOSITION)
                .bindOn(aMh.class)
                .onRun(context -> prompt((aMh) context.widget(), label, duplicate));
    }

    private static void prompt(aMh widget, String label, boolean duplicate) {
        try {
            if (!selectedNotes(widget).isEmpty()) {
                new IntervalDialog(label, semitones -> run(widget, label,
                        notes -> IntervalEdit.plan(label, notes, semitones, duplicate)))
                        .FzB(widget); // show modal
            }
        } catch (Refused refused) {
            Notifications.toast(label + ": " + refused.getMessage());
        }
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
