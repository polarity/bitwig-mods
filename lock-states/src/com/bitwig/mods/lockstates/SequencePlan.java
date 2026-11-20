package com.bitwig.mods.lockstates;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import java.util.HashMap;

/** A device-wide, stepped sequence in clip-content beats. No UI polling or playback-time document edits. */
public final class SequencePlan {
    public static final int MAX_POINTS = 262144;
    public record Program(Map<String, List<Point>> changes) {
        public Program { changes = Map.copyOf(changes); }
        public List<Point> points(String key, double baseline) {
            if (!Double.isFinite(baseline) || baseline < 0 || baseline > 1) throw new IllegalArgumentException("Invalid sequence baseline");
            var values = changes.getOrDefault(key, List.of());
            if (values.isEmpty() || values.getFirst().time() == 0) return values;
            var result = new ArrayList<Point>(); result.add(new Point(0, baseline)); result.addAll(values);
            return List.copyOf(result);
        }
    }
    public record Event(double time, String state) {
        public Event { if (!Double.isFinite(time) || time < 0) throw new IllegalArgumentException("Invalid bound note time"); StateBank.uuid(state); }
    }
    public record Point(double time, double value) {
        public Point { if (!Double.isFinite(time) || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid sequence point"); }
    }

    public static List<Event> events(List<Event> notes, StateBank bank) {
        if (notes.size() > StateBank.MAX_PARAMETERS) throw new IllegalArgumentException("Too many bound notes");
        var ordered = new TreeMap<Double, String>();
        for (var event : notes) {
            if (bank.states().stream().noneMatch(state -> state.id().equals(event.state()))) continue; // deleted states become inactive assignments
            String previous = ordered.putIfAbsent(event.time(), event.state());
            if (previous != null && !previous.equals(event.state())) {
                throw new IllegalArgumentException("Different Lock States start together on this device. Assign the same state to simultaneous notes.");
            }
        }
        return ordered.entrySet().stream().map(entry -> new Event(entry.getKey(), entry.getValue())).toList();
    }

    public static List<Point> points(List<Event> notes, StateBank bank, StateBank.Parameter parameter,
                                     StateBank.Layout current, double baseline) {
        return compile(notes, bank, current).points(parameter.key(), baseline);
    }

    public static Program compile(List<Event> notes, StateBank bank, StateBank.Layout current) {
        var currentParameters = new HashMap<String, StateBank.Parameter>(); current.parameters().forEach(parameter -> currentParameters.put(parameter.key(), parameter));
        var currentNodes = new HashMap<String, String>(); current.nodes().forEach(node -> currentNodes.put(node.path(), node.type()));
        var states = new HashMap<String, StateBank.State>(); bank.states().forEach(state -> states.put(state.id(), state));
        var compatible = new HashMap<String, Map<String, Double>>();
        var changes = new HashMap<String, List<Point>>();
        int total = 0;
        for (var event : events(notes, bank)) {
            var values = compatible.computeIfAbsent(event.state(), id -> {
                var state = states.get(id); var layout = bank.layoutOf(state);
                var nodes = new HashMap<String, String>(); layout.nodes().forEach(node -> nodes.put(node.path(), node.type()));
                var found = new HashMap<String, Double>();
                if (nodes.containsKey("root") && !nodes.get("root").equals(currentNodes.get("root"))) return found;
                for (var parameter : layout.parameters()) {
                    var target = currentParameters.get(parameter.key());
                    if (target != null && parameter.compatible(target) && Recall.nodesMatch(parameter.key(), nodes, currentNodes)) found.put(parameter.key(), state.values().get(parameter.key()));
                }
                return Map.copyOf(found);
            });
            total += values.size();
            if (total > MAX_POINTS) throw new IllegalArgumentException("This clip's Lock State sequence exceeds 262,144 parameter changes");
            values.forEach((key, value) -> changes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Point(event.time(), value)));
        }
        var immutable = new HashMap<String, List<Point>>(); changes.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return new Program(immutable);
    }
}
