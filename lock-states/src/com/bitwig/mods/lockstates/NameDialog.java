package com.bitwig.mods.lockstates;

import bitwig.obf.dWb; // widget layout
import bitwig.obf.dtX; // native modal dialog
import bitwig.obf.dve; // parent widget
import bitwig.obf.ecV; // native text editor with a local value
import bml.notifications.Notifications;
import java.util.function.Consumer;

final class NameDialog extends dtX {
    private final ecV field;
    private final Consumer<String> accept;
    private boolean done;

    private NameDialog(String title, String initial, Consumer<String> accept) {
        this.accept = accept;
        zqN(); // initialize the native modal
        MJ(title); // title
        BDm(true); // OK
        jFL(true); // Cancel
        field = ecV.bB(); // independent native text editor
        field.IwV(initial); // initial text
        dWb body = dWb.UNj(6);
        body.HL(field);
        IwV((dve) body); // attach the body
        H_(); // finish before displaying
    }

    static void show(Object parent, String title, String initial, Consumer<String> accept) {
        if (!(parent instanceof dve widget)) throw new IllegalArgumentException("Open the Device Inspector first");
        new NameDialog(title, initial, accept).FzB(widget); // modal in this window
    }

    @Override public void ke_() { // native OK, including Enter
        if (done) return;
        String name = field.JqA().strip(); // editor text
        if (name.isEmpty() || name.length() > 120) {
            Notifications.toast("Use a name between 1 and 120 characters.");
            return;
        }
        done = true;
        super.ke_();
        NativeDevice.later(() -> accept.accept(name));
    }
}
