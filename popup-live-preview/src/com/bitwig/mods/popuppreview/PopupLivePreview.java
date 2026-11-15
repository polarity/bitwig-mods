package com.bitwig.mods.popuppreview;

import java.util.IdentityHashMap;
import java.util.Map;

import bitwig.obf.NVD; // value change listener
import bitwig.obf.aYL; // popup browsing context
import bitwig.obf.aYN; // popup browsing session
import bitwig.obf.lIU; // observable, writable boolean value
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Settings;
import bml.api.This;

public final class PopupLivePreview {
    private static final Bml.Mod MOD = Bml.mod("popup-live-preview");
    private static final Settings STORE = MOD.settings();
    private static final int REMEMBER = 0;
    private static final int OPEN_ON = 1;
    private static final int OPEN_OFF = 2;
    private static final String MODE_KEY = "live-preview-mode";
    private static final String LAST_CHOICE_KEY = "last-preview-choice";

    // A session owns its listener. All session hooks run on Bitwig's UI thread.
    private static final Map<aYN, PreviewListener> LISTENERS = new IdentityHashMap<>();

    private record PreviewListener(lIU value, NVD listener) {
        void detach() {
            value.removeListener(listener);
        }
    }

    private PopupLivePreview() {}

    static int mode() {
        int mode = STORE.getInt(MODE_KEY, REMEMBER);
        return mode == OPEN_ON || mode == OPEN_OFF ? mode : REMEMBER;
    }

    static void setMode(int mode) {
        STORE.putInt(MODE_KEY, mode);
    }

    // Bitwig creates the context's preview value and then calls this method
    // before applying the initial query or publishing the popup session.
    @Hook(target = aYN.class, method = "Bqk(Z)V", at = At.ENTRY, id = "initialize-live-preview")
    public static void initialize(@This aYN session, @Arg(0) boolean initialQuery) {
        if (!initialQuery) {
            return;
        }
        try {
            aYL context = session.Bqk(); // active browsing context
            lIU preview = context.gNZ(); // the same value bound to Live Preview
            if (preview == null || !preview.isSettable()) {
                return;
            }
            int mode = mode();
            boolean current = preview.getBoolValue();
            boolean enabled = switch (mode) {
                case OPEN_ON -> true;
                case OPEN_OFF -> false;
                default -> STORE.getBoolean(LAST_CHOICE_KEY, current);
            };
            if (current != enabled) {
                preview.setBoolValue(enabled);
            }
            if (mode == REMEMBER && preview.isObservable()) {
                // Subscribe after applying the starting state so only subsequent
                // changes can replace the remembered choice.
                NVD listener = event -> STORE.putBoolean(LAST_CHOICE_KEY, preview.getBoolValue());
                preview.addListener(listener);
                LISTENERS.put(session, new PreviewListener(preview, listener));
            }
        } catch (RuntimeException | LinkageError e) {
            MOD.log("Could not initialize popup Live Preview", e);
        }
    }

    @Hook(target = aYN.class, method = "ymo()V", at = At.ENTRY, id = "detach-preview-before-commit")
    public static void beforeCommit(@This aYN session) {
        detach(session);
    }

    @Hook(target = aYN.class, method = "PJ3()V", at = At.ENTRY, id = "detach-preview-before-dispose")
    public static void beforeDispose(@This aYN session) {
        detach(session);
    }

    private static void detach(aYN session) {
        PreviewListener listener = LISTENERS.remove(session);
        if (listener != null) {
            try {
                listener.detach();
            } catch (RuntimeException | LinkageError e) {
                MOD.log("Could not detach the popup Live Preview listener", e);
            }
        }
    }
}
