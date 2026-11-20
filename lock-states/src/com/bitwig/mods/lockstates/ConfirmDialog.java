package com.bitwig.mods.lockstates;

import bitwig.obf.dtX; // native OK/Cancel modal
import bitwig.obf.duY; // native text label
import bitwig.obf.dve; // parent widget

final class ConfirmDialog extends dtX {
    private final Runnable accept;
    private boolean done;

    private ConfirmDialog(String title, String text, Runnable accept) {
        this.accept = accept;
        zqN(); // initialize the modal
        MJ(title); // title
        BDm(true); // OK
        jFL(true); // Cancel
        IwV((dve) duY.MIO(text)); // body
        H_(); // finish before displaying
    }

    static void show(Object parent, String title, String text, Runnable accept) {
        if (!(parent instanceof dve widget)) throw new IllegalArgumentException("Open the Device Inspector first");
        new ConfirmDialog(title, text, accept).FzB(widget);
    }

    @Override public void ke_() { // OK
        if (done) return;
        done = true;
        super.ke_();
        NativeDevice.later(accept);
    }
}
