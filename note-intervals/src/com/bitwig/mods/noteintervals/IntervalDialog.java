package com.bitwig.mods.noteintervals;

import bitwig.obf.dWb; // widget layout group
import bitwig.obf.dtX; // native modal dialog
import bitwig.obf.dve; // widget base type
import bitwig.obf.duY; // text label
import bitwig.obf.ecl; // message label
import bitwig.obf.ecV; // single-line text editor
import bitwig.obf.edw; // text editor commit event
import java.util.function.IntConsumer;

final class IntervalDialog extends dtX {
    private final IntConsumer onConfirm;
    private final ecV input;
    private final ecl validation;
    private boolean confirmed;

    IntervalDialog(String title, IntConsumer onConfirm) {
        this.onConfirm = onConfirm;
        zqN(); // initialize the modal
        MJ(title); // set the title
        BDm(true); // show OK
        jFL(true); // show Cancel

        input = ecV.bB();
        validation = ecl.cfM("Enter a nonzero whole number from -127 to +127.");
        validation.t_(false); // show validation only after an invalid submission
        dWb body = dWb.UNj(6); // vertical layout
        body.HL(duY.MIO("Semitones (for example, +4 or -3)"));
        body.HL(input);
        body.HL(validation);
        IwV((dve) body); // select the content overload, as in Bitwig's own text dialogs
        MRY(input); // initial keyboard focus
        input.IwV(event -> { // the field handles Enter before the modal sees it
            if (event instanceof edw commit) {
                confirm(commit.Hnt()); // submitted text
            }
        });
        H_(); // finish initialization before showing the modal
    }

    @Override
    public void ke_() { // OK button
        confirm(input.xlY().NMA()); // current text, including an uncommitted edit
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
        super.ke_(); // close before editing the notes
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
