package com.bitwig.mods.lockstates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** Identities are scoped to one snapshot owner. Whole-tree copies retain them; copies inside that tree get new IDs. */
public final class DeviceIdentity {
    public record Source(String stored, String nativeId) { }
    public record Identity(String token, String stored) { }

    public static List<Identity> plan(List<Source> sources) {
        var result = new ArrayList<Identity>();
        var groups = new HashMap<String, List<Integer>>();
        for (int i = 0; i < sources.size(); i++) {
            Source source = sources.get(i);
            StateBank.uuid(source.nativeId());
            String stored = source.stored();
            if (stored.isEmpty()) stored = UUID.randomUUID() + "/" + source.nativeId();
            String[] parts = stored.split("/", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid saved Lock States device identity");
            StateBank.uuid(parts[0]); StateBank.uuid(parts[1]);
            result.add(new Identity(parts[0], stored));
            groups.computeIfAbsent(parts[0], ignored -> new ArrayList<>()).add(i);
        }
        for (var group : groups.values()) {
            if (group.size() == 1) {
                int index = group.getFirst();
                Identity identity = result.get(index);
                result.set(index, new Identity(identity.token(), identity.token() + "/" + sources.get(index).nativeId()));
                continue; // rebase a whole-tree copy's native UUID without changing its snapshot keys
            }
            var originals = group.stream().filter(index -> result.get(index).stored().endsWith("/" + sources.get(index).nativeId())).toList();
            if (originals.size() != 1) {
                throw new IllegalArgumentException("Ambiguous copied device identities. Undo the device copy and capture again before duplicating it.");
            }
            for (int index : group) if (index != originals.getFirst()) {
                String token = UUID.randomUUID().toString();
                result.set(index, new Identity(token, token + "/" + sources.get(index).nativeId()));
            }
        }
        return List.copyOf(result);
    }
}
