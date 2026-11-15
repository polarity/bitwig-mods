package com.bitwig.mods.noteintervals;

import bitwig.obf.aNi; // Detail Editor widget
import bitwig.obf.bjw; // Detail Editor model
import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.notifications.Notifications;
import bml.pianoroll.NoteSelection;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;

public final class IntervalActions implements ActionProvider {
    private static final String CATEGORY = "note_intervals";

    @Override
    public void declare(Actions actions) {
        actions.category(CATEGORY, "Note Intervals");
        add(actions, "transpose_fifth_up", "Transpose a Fifth Up", 7, false);
        add(actions, "transpose_fifth_down", "Transpose a Fifth Down", -7, false);
        add(actions, "duplicate_fifth_up", "Duplicate a Fifth Up", 7, true);
        add(actions, "duplicate_fifth_down", "Duplicate a Fifth Down", -7, true);
        add(actions, "duplicate_octave_up", "Duplicate an Octave Up", 12, true);
        add(actions, "duplicate_octave_down", "Duplicate an Octave Down", -12, true);
    }

    private static void add(Actions actions, String id, String label, int semitones, boolean duplicate) {
        actions.action(CATEGORY + "_" + id, label)
                .category(CATEGORY)
                .bindOn(aNi.class)
                .onRun(context -> run((aNi) context.widget(), label, semitones, duplicate));
    }

    private static void run(aNi widget, String label, int semitones, boolean duplicate) {
        bjw editor = widget.yAy(); // model of the editor holding the action binding
        if (editor == null) {
            return;
        }
        try {
            // Read the active selection of this editor, including the time selection if that tool is active.
            NoteSelection selection = PianoRoll.selection(editor.BBg());
            if (!selection.hasOtherItems() && !selection.notes().isEmpty()) {
                IntervalEdit.plan(label, selection.notes(), semitones, duplicate).apply();
            }
        } catch (Refused refused) {
            Notifications.toast(label + ": " + refused.getMessage());
        }
    }
}
