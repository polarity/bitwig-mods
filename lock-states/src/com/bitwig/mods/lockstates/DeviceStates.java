package com.bitwig.mods.lockstates;

import java.io.IOException;

/** One instance's complete persisted payload. No project paths or shared library IDs are used for ownership. */
public record DeviceStates(int version, StateBank snapshots, String selected, boolean notePlayback) {
    public DeviceStates(int version, StateBank snapshots, String selected) { this(version, snapshots, selected, false); }
    public static final int VERSION = 2;
    public static final int MAX_CHARACTERS = 32 * 1024 * 1024;

    public DeviceStates {
        if ((version != 1 && version != VERSION) || snapshots == null || selected == null || (version == 1 && notePlayback)) {
            throw new IllegalArgumentException("Unsupported device states data");
        }
        if (!selected.isEmpty() && snapshots.states().stream().noneMatch(state -> state.id().equals(selected))) {
            throw new IllegalArgumentException("The selected state does not belong to this device");
        }
    }

    public static DeviceStates empty(StateBank.Layout layout) {
        return new DeviceStates(VERSION, StateBank.empty("Device States", layout), "");
    }

    public DeviceStates with(StateBank updated, String selection) { return new DeviceStates(VERSION, updated, selection, notePlayback); }
    public DeviceStates playback(boolean enabled) { return new DeviceStates(VERSION, snapshots, selected, enabled); }

    public String encode() {
        String encoded = BankStore.encodeDocument(this);
        if (encoded.length() > MAX_CHARACTERS) throw new IllegalArgumentException("Device states exceed 32 MiB characters");
        return encoded;
    }

    public static DeviceStates decode(String text) {
        try { return BankStore.decode(text, DeviceStates.class); }
        catch (IOException error) { throw new IllegalArgumentException("Cannot read this device's Lock States: " + error.getMessage(), error); }
    }
}
