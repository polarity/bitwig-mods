package com.bitwig.mods.lockstates;

import bitwig.obf.dAa; // Inspector push button
import bitwig.obf.dWb; // widget layout group
import bitwig.obf.dve; // widget base type
import bitwig.obf.kn; // a size that fills the width
import bitwig.obf.w3; // Bitwig's Inspector widget styles
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Inspector widgets the composition framework does not offer: relabelled buttons and two buttons per row. */
final class Ui {
    /** Marks the active entry of a button list; button colours are not available through the framework. */
    static final String ACTIVE = "● ";
    /** Buttons shown as a direct list. Longer lists continue in a dropdown. */
    static final int SLOTS = 16;

    private Ui() { }

    /** A button whose label and enabled state are read again by every updater run. */
    static dAa button(List<Runnable> updaters, Supplier<String> label, BooleanSupplier enabled, Runnable click) {
        dAa button = dAa.hbP(label.get()); // as Bitwig's Inspector buttons
        button.a_(kn.cfM());
        button.MRY(w3.MRY);
        button.Hnt(click);
        Runnable update = () -> {
            button.IwV(label.get()); // its label
            button.nOs().setBoolValue(enabled.getAsBoolean()); // the widget's enabled property
        };
        update.run();
        updaters.add(update);
        return button;
    }

    /** Two widgets sharing one row. */
    static Object pair(Object left, Object right) {
        dWb row = dWb.IwV(3); // horizontal layout with the Inspector spacing
        row.HL((dve) left);
        row.HL((dve) right);
        row.a_(kn.cfM());
        return row;
    }
}
