package com.bitwig.mods.chordtools;

import bitwig.obf.dAa; // push button
import bitwig.obf.dWb; // widget layout group
import bitwig.obf.dtX; // native modal dialog
import bitwig.obf.dve; // widget base type
import bitwig.obf.duY; // text label
import java.util.List;
import java.util.function.IntConsumer;

final class ChordRootDialog extends dtX {
    private static final String[] PITCH_NAMES = {"C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B"};
    private static final int ROOTS_PER_ROW = 4;
    private boolean chosen;

    ChordRootDialog(String title, String chord, List<Integer> roots, IntConsumer onChoose) {
        zqN(); // initialize the modal
        MJ(title); // set the title
        jFL(true); // show Cancel
        dWb body = dWb.UNj(6); // vertical layout
        body.HL(duY.MIO(chord));
        body.HL(duY.MIO("Choose the chord root"));
        for (int start = 0; start < roots.size(); start += ROOTS_PER_ROW) {
            dWb row = dWb.IwV(6); // horizontal layout
            for (int i = start; i < Math.min(roots.size(), start + ROOTS_PER_ROW); i++) {
                int root = roots.get(i);
                row.HL(dAa.cfM(pitchName(root), () -> choose(root, onChoose)));
            }
            body.HL(row);
        }
        IwV((dve) body); // attach to the content slot
        H_(); // finish initialization before showing the modal
    }

    static String pitchName(int pitch) {
        return PITCH_NAMES[pitch % PITCH_NAMES.length];
    }

    private void choose(int root, IntConsumer onChoose) {
        if (!chosen) {
            chosen = true;
            super.ke_(); // close the dialog before continuing the edit
            onChoose.accept(root);
        }
    }
}
