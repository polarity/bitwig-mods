package com.bitwig.mods.lockstates;

import java.util.LinkedHashMap;
import java.util.Map;

/** Position-based dev.4 snapshots can only be assigned identities while their complete original tree still matches. */
public final class StateMigration {
    public static StateBank upgrade(StateBank bank, StateBank.Layout legacy, StateBank.Layout stable, Map<String, String> keys) {
        if (bank.formatVersion() == StateBank.FORMAT && bank.states().stream().allMatch(state -> state.layout() != null)) return bank;
        try { bank.layout().requireCompatible(legacy); }
        catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("These older states need a one-time upgrade. Restore the original chain, then Recall or Capture once before changing its devices.", failure);
        }
        if (!keys.keySet().equals(bank.layout().parameters().stream().map(StateBank.Parameter::key).collect(java.util.stream.Collectors.toSet()))
                || !new java.util.HashSet<>(keys.values()).equals(stable.parameters().stream().map(StateBank.Parameter::key).collect(java.util.stream.Collectors.toSet()))
                || new java.util.HashSet<>(keys.values()).size() != keys.size()) throw new IllegalArgumentException("Incomplete state identity migration");
        var states = bank.states().stream().map(state -> {
            if (state.layout() != null) return state;
            var values = new LinkedHashMap<String, Double>();
            state.values().forEach((key, value) -> values.put(keys.get(key), value));
            return new StateBank.State(state.id(), state.name(), values, stable);
        }).toList();
        return new StateBank(StateBank.FORMAT, bank.id(), bank.name(), stable, states);
    }
}
