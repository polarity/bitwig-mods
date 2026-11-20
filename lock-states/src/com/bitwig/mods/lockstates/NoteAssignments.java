package com.bitwig.mods.lockstates;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lives on the native note, so moving/copying a note carries its assignments. Owner tokens are scoped to its track. */
public record NoteAssignments(int version, Map<String, String> states) {
    public NoteAssignments {
        if (version != 1 || states == null || states.size() > 256) throw new IllegalArgumentException("Unsupported note state assignments");
        states = Map.copyOf(states);
        states.forEach((owner, state) -> { StateBank.uuid(owner); StateBank.uuid(state); });
    }
    public static NoteAssignments empty() { return new NoteAssignments(1, Map.of()); }
    public NoteAssignments with(String owner, String state) {
        var updated = new LinkedHashMap<>(states);
        if (state == null) updated.remove(owner); else updated.put(owner, state);
        return new NoteAssignments(1, updated);
    }
    public String encode() { return states.isEmpty() ? "" : BankStore.encodeDocument(this); }
    public static NoteAssignments decode(String data) {
        if (data.isEmpty()) return empty();
        try { return BankStore.decode(data, NoteAssignments.class); }
        catch (IOException failure) { throw new IllegalArgumentException("Cannot read this note's state assignment", failure); }
    }
}
