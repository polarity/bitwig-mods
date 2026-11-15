package com.bitwig.mods.chordtools;

import bitwig.obf.dBz; // push button
import bitwig.obf.dXL; // widget layout group
import bitwig.obf.dvv; // native modal dialog
import bitwig.obf.dwF; // widget base type
import bitwig.obf.dwy; // text label
import java.util.List;
import java.util.function.IntConsumer;

final class ChordRootDialog extends dvv {
    private static final String[] PITCH_NAMES = {"C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B"};
    private static final int ROOTS_PER_ROW = 4;
    private boolean chosen;

    ChordRootDialog(String title, String chord, List<Integer> roots, IntConsumer onChoose) {
        XhI(); // initialize the modal
        neH(title); // set the title
        Ml2(true); // show Cancel
        dXL body = dXL.unz(6); // vertical layout
        body.IvP(dwy.LYs(chord));
        body.IvP(dwy.LYs("Choose the chord root"));
        for (int start = 0; start < roots.size(); start += ROOTS_PER_ROW) {
            dXL row = dXL.BBg(6); // horizontal layout
            for (int i = start; i < Math.min(roots.size(), start + ROOTS_PER_ROW); i++) {
                int root = roots.get(i);
                row.IvP(dBz.beI(pitchName(root), () -> choose(root, onChoose)));
            }
            body.IvP(row);
        }
        BBg((dwF) body); // attach to the content slot
        I_(); // finish initialization before showing the modal
    }

    static String pitchName(int pitch) {
        return PITCH_NAMES[pitch % PITCH_NAMES.length];
    }

    private void choose(int root, IntConsumer onChoose) {
        if (!chosen) {
            chosen = true;
            super.kf_(); // close the dialog before continuing the edit
            onChoose.accept(root);
        }
    }
}
