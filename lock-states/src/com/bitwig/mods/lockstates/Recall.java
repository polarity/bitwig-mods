package com.bitwig.mods.lockstates;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

/** Validation and rollback are independent of Bitwig, so failure paths can be exercised offline. */
public final class Recall {
    public interface Target {
        StateBank.Parameter descriptor();
        boolean writable();
        boolean automated();
        double read();
        void write(double normalized);
    }

    public interface Transaction {
        void run(String label, Runnable change);
    }

    private Recall() { }

    public static Map<String, Double> capture(List<? extends Target> targets) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Target target : targets) {
            if (!target.writable()) throw new IllegalArgumentException("Parameter is not writable: " + target.descriptor().name());
            double value = target.read();
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid current value");
            if (values.put(target.descriptor().key(), value) != null) throw new IllegalArgumentException("Duplicate parameter");
        }
        return values;
    }

    public record Result(int applied, int skipped, int untouched) { }

    public static Result apply(StateBank bank, StateBank.State state, StateBank.Layout current,
                             List<? extends Target> targets, boolean automationWriting, Transaction transaction) {
        return apply(bank, state, current, targets, automationWriting, transaction, () -> { });
    }

    public static Result apply(StateBank bank, StateBank.State state, StateBank.Layout current,
                             List<? extends Target> targets, boolean automationWriting, Transaction transaction, Runnable afterApply) {
        StateBank.Layout saved = bank.layoutOf(state);
        if (state.layout() == null) saved.requireCompatible(current); // legacy snapshots require an exact one-time migration
        if (automationWriting) throw new IllegalArgumentException("Turn off automation recording before recalling a state.");
        if (targets.size() != current.parameters().size()) throw new IllegalArgumentException("Incomplete target list");
        var savedParameters = new HashMap<String, StateBank.Parameter>();
        saved.parameters().forEach(parameter -> savedParameters.put(parameter.key(), parameter));
        var currentNodes = new HashMap<String, String>();
        current.nodes().forEach(node -> currentNodes.put(node.path(), node.type()));
        var savedNodes = saved.nodes();
        var savedNodeTypes = new HashMap<String, String>();
        savedNodes.forEach(node -> savedNodeTypes.put(node.path(), node.type()));
        var savedRoot = savedNodes.stream().filter(node -> node.path().equals("root")).findFirst();
        if (savedRoot.isPresent() && !savedRoot.get().type().equals(currentNodes.get("root"))) {
            throw new IllegalArgumentException("This state belongs to a different parent device type");
        }
        var matched = new ArrayList<Target>();
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            if (!current.parameters().get(i).compatible(target.descriptor())) throw new IllegalArgumentException("Target order changed");
            var stored = savedParameters.get(target.descriptor().key());
            if (stored == null || !stored.compatible(target.descriptor())) continue;
            if (!nodesMatch(stored.key(), savedNodeTypes, currentNodes)) continue;
            if (target.automated()) throw new IllegalArgumentException("Recall blocked by automation: " + target.descriptor().name());
            matched.add(target);
        }
        // Validate even callers that pass a State not belonging to the bank.
        new StateBank(bank.formatVersion(), bank.id(), bank.name(), bank.layout(), List.of(state));
        if (matched.isEmpty()) throw new IllegalArgumentException("No compatible saved parameters remain in this device");
        Map<String, Double> before = capture(matched);
        transaction.run("Recall Lock State: " + state.name(), () -> {
            try {
                for (Target target : matched) {
                    target.write(state.values().get(target.descriptor().key()));
                }
                verify(matched, state.values());
                afterApply.run(); // persist the selected state in the same Undo step; failure restores parameter values
            } catch (RuntimeException | LinkageError error) {
                RuntimeException rollback = null;
                // Restore every target: one setter can also affect another parameter.
                for (int i = matched.size() - 1; i >= 0; i--) {
                    Target target = matched.get(i);
                    try { target.write(before.get(target.descriptor().key())); }
                    catch (RuntimeException | LinkageError failure) {
                        if (rollback == null) rollback = new IllegalStateException("State rollback failed. Use Undo and check the device.");
                        rollback.addSuppressed(failure);
                    }
                }
                try { verify(matched, before); }
                catch (RuntimeException failure) {
                    if (rollback == null) rollback = new IllegalStateException("State rollback verification failed. Use Undo.");
                    rollback.addSuppressed(failure);
                }
                if (rollback != null) { rollback.addSuppressed(error); throw rollback; }
                throw new IllegalStateException("Recall failed; previous parameter values were restored.", error);
            }
        });
        return new Result(matched.size(), saved.parameters().size() - matched.size(), targets.size() - matched.size());
    }

    static boolean nodesMatch(String key, Map<String, String> saved, Map<String, String> current) {
        int separator = key.lastIndexOf('/');
        while (separator >= 0) {
            String prefix = key.substring(0, separator);
            String type = saved.get(prefix);
            if (type != null && !type.equals(current.get(prefix))) return false;
            separator = key.lastIndexOf('/', separator - 1);
        }
        return true;
    }

    static boolean compatible(StateBank.Parameter saved, StateBank.Layout savedLayout, StateBank.Parameter current, StateBank.Layout currentLayout) {
        if (!saved.compatible(current)) return false;
        var savedNodes = new HashMap<String, String>(); var currentNodes = new HashMap<String, String>();
        savedLayout.nodes().forEach(node -> savedNodes.put(node.path(), node.type()));
        currentLayout.nodes().forEach(node -> currentNodes.put(node.path(), node.type()));
        if (savedNodes.containsKey("root") && !savedNodes.get("root").equals(currentNodes.get("root"))) return false;
        return nodesMatch(saved.key(), savedNodes, currentNodes);
    }

    private static void verify(List<? extends Target> targets, Map<String, Double> expected) {
        for (Target target : targets) {
            double value = target.read();
            StateBank.Parameter descriptor = target.descriptor();
            double wanted = expected.get(descriptor.key());
            boolean different = descriptor.kind().equals("discrete") && descriptor.steps() > 1
                    ? Math.round(value * (descriptor.steps() - 1)) != Math.round(wanted * (descriptor.steps() - 1))
                    : Math.abs(value - wanted) > 1e-6;
            if (!Double.isFinite(value) || value < 0 || value > 1 || different) {
                throw new IllegalStateException("Parameter did not accept its value: " + target.descriptor().name());
            }
        }
    }
}
