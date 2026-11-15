package com.bitwig.mods.noteintervals;

import bitwig.obf.dXL; // widget layout group
import bitwig.obf.dvv; // native modal dialog
import bitwig.obf.dwF; // widget base type
import bitwig.obf.dwy; // text label
import bitwig.obf.edN; // message label
import bitwig.obf.eey; // single-line text editor
import bitwig.obf.efb; // text editor commit event
import java.util.function.IntConsumer;

final class IntervalDialog extends dvv {
    private final IntConsumer onConfirm;
    private final eey input;
    private final edN validation;
    private boolean confirmed;

    IntervalDialog(String title, IntConsumer onConfirm) {
        this.onConfirm = onConfirm;
        XhI(); // initialize the modal
        neH(title); // set the title
        Bqd(true); // show OK
        Ml2(true); // show Cancel

        input = eey.qVz();
        validation = edN.beI("Enter a nonzero whole number from -127 to +127.");
        validation.t_(false); // show validation only after an invalid submission
        dXL body = dXL.unz(6); // vertical layout
        body.IvP(dwy.LYs("Semitones (for example, +4 or -3)"));
        body.IvP(input);
        body.IvP(validation);
        BBg((dwF) body); // select the content overload, as in Bitwig's own text dialogs
        Gef(input); // initial keyboard focus
        input.BBg(event -> { // the field handles Enter before the modal sees it
            if (event instanceof efb commit) {
                confirm(commit.sCA()); // submitted text
            }
        });
        I_(); // finish initialization before showing the modal
    }

    @Override
    public void kf_() { // OK button
        confirm(input.Iba().ERa()); // current text, including an uncommitted edit
    }

    private void confirm(String text) {
        if (confirmed) {
            return;
        }
        int semitones;
        try {
            semitones = parseSemitones(text);
        } catch (NumberFormatException invalid) {
            validation.t_(true);
            return;
        }
        confirmed = true;
        super.kf_(); // close before editing the notes
        onConfirm.accept(semitones);
    }

    static int parseSemitones(String text) {
        int value = Integer.parseInt(text.strip().replace('\u2212', '-'));
        if (value < -127 || value > 127 || value == 0) {
            throw new NumberFormatException("Invalid semitone interval");
        }
        return value;
    }
}
