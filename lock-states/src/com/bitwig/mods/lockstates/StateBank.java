package com.bitwig.mods.lockstates;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Portable parameter snapshots. Keys use owner-relative identities and model roles, never Java classes or display labels. */
public record StateBank(int formatVersion, String id, String name, Layout layout, List<State> states) {
    public static final int FORMAT = 2;
    public static final int MAX_PARAMETERS = 16384;
    public static final int MAX_STATES = 256;

    public record Node(String path, String type) {
        public Node { text(path); text(type); }
    }

    /** minimum/maximum retain their format-1 names: raw values at normalized 0/1, in native order. */
    public record Parameter(String key, String name, String kind, long steps,
                            double minimum, double maximum, List<String> choices) {
        public Parameter {
            text(key); text(name);
            if (!Set.of("continuous", "discrete").contains(kind) || steps < 0
                    || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                    || (kind.equals("discrete") && steps < 1) || (kind.equals("continuous") && steps != 0)) {
                throw new IllegalArgumentException("Unsupported range for '" + name + "': " + kind
                        + ", steps=" + steps + ", values=" + minimum + ".." + maximum);
            }
            choices = List.copyOf(choices);
            if (choices.size() > MAX_PARAMETERS) throw new IllegalArgumentException("Too many choices");
        }

        boolean compatible(Parameter other) {
            return key.equals(other.key) && kind.equals(other.kind) && steps == other.steps
                    && Double.compare(minimum, other.minimum) == 0
                    && Double.compare(maximum, other.maximum) == 0;
        }
    }

    public record Layout(List<Node> nodes, List<Parameter> parameters) {
        public Layout {
            nodes = List.copyOf(nodes); parameters = List.copyOf(parameters);
            if (nodes.isEmpty() || nodes.size() > MAX_PARAMETERS || parameters.isEmpty()
                    || parameters.size() > MAX_PARAMETERS) throw new IllegalArgumentException("Invalid device layout size");
            unique(nodes.stream().map(Node::path).toList());
            unique(parameters.stream().map(Parameter::key).toList());
        }

        public void requireCompatible(Layout other) {
            if (!nodes.equals(other.nodes) || parameters.size() != other.parameters.size()) {
                throw new IllegalArgumentException("The device structure changed. Restore its structure or delete its old states before capturing a new layout.");
            }
            for (int i = 0; i < parameters.size(); i++) {
                if (!parameters.get(i).compatible(other.parameters.get(i))) {
                    throw new IllegalArgumentException("The parameter layout changed: " + parameters.get(i).name);
                }
            }
        }
    }

    public record State(String id, String name, Map<String, Double> values, Layout layout) {
        public State(String id, String name, Map<String, Double> values) { this(id, name, values, null); }
        public State {
            uuid(id); text(name); values = Map.copyOf(values);
            for (var entry : values.entrySet()) {
                text(entry.getKey());
                if (!Double.isFinite(entry.getValue()) || entry.getValue() < 0 || entry.getValue() > 1) {
                    throw new IllegalArgumentException("Invalid parameter value: " + entry.getKey());
                }
            }
        }
    }

    public StateBank {
        if (formatVersion != 1 && formatVersion != FORMAT) throw new IllegalArgumentException("Unsupported bank format: " + formatVersion);
        uuid(id); text(name); states = List.copyOf(states);
        if (layout == null || states.size() > MAX_STATES) throw new IllegalArgumentException("Invalid bank");
        unique(states.stream().map(State::id).toList());
        for (State state : states) {
            Layout snapshot = state.layout == null ? layout : state.layout;
            if (formatVersion == 1 && state.layout != null) throw new IllegalArgumentException("Legacy state contains an unexpected layout");
            Set<String> keys = new HashSet<>(snapshot.parameters.stream().map(Parameter::key).toList());
            if (!state.values.keySet().equals(keys)) throw new IllegalArgumentException("Incomplete state: " + state.name);
            for (Parameter p : snapshot.parameters) {
                double value = state.values.get(p.key);
                if (p.kind.equals("discrete") && p.steps == 1 && value != 0) {
                    throw new IllegalArgumentException("Invalid singleton value: " + p.name);
                }
                if (p.kind.equals("discrete") && p.steps > 1
                        && Math.abs(value * (p.steps - 1) - Math.rint(value * (p.steps - 1))) > 1e-6) {
                    throw new IllegalArgumentException("Invalid discrete value: " + p.name);
                }
            }
        }
    }

    public static StateBank empty(String name, Layout layout) {
        return new StateBank(FORMAT, UUID.randomUUID().toString(), name, layout, List.of());
    }

    public StateBank withStates(List<State> next) { return new StateBank(formatVersion, id, name, layout, next); }

    public Layout layoutOf(State state) { return state.layout == null ? layout : state.layout; }

    public static State capture(String name, Map<String, Double> values) {
        return new State(UUID.randomUUID().toString(), name, new TreeMap<>(values));
    }

    public static State capture(String name, Map<String, Double> values, Layout layout) {
        return new State(UUID.randomUUID().toString(), name, new TreeMap<>(values), layout);
    }

    static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 4096) throw new IllegalArgumentException("Invalid text");
    }

    static void uuid(String value) {
        if (value == null || !UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("Invalid ID");
    }

    private static void unique(List<String> keys) {
        if (new HashSet<>(keys).size() != keys.size()) throw new IllegalArgumentException("Duplicate identifier");
    }
}
