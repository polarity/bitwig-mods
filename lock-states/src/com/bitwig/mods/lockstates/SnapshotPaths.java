package com.bitwig.mods.lockstates;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts model-role paths into owner-relative device identities, retaining the role/type of internal modules. */
public final class SnapshotPaths {
    public record Mapping(StateBank.Layout layout, Map<String, String> keys) { }

    public static Mapping map(StateBank.Layout legacy, Map<String, String> tokens) {
        var paths = new LinkedHashMap<String, String>();
        var nodes = new ArrayList<StateBank.Node>();
        for (var node : legacy.nodes().stream().sorted(java.util.Comparator.comparingInt(node -> node.path().length())).toList()) {
            String path = tokens.containsKey(node.path()) ? "device/" + tokens.get(node.path()) : remap(node.path(), paths);
            if (node.type().startsWith("module:")) path += "/module/" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(node.type().getBytes(StandardCharsets.UTF_8));
            paths.put(node.path(), path); nodes.add(new StateBank.Node(path, node.type()));
        }
        var keys = new LinkedHashMap<String, String>();
        var parameters = new ArrayList<StateBank.Parameter>();
        for (var parameter : legacy.parameters()) {
            String key = remap(parameter.key(), paths); keys.put(parameter.key(), key);
            parameters.add(new StateBank.Parameter(key, parameter.name(), parameter.kind(), parameter.steps(), parameter.minimum(), parameter.maximum(), parameter.choices()));
        }
        nodes.sort(java.util.Comparator.comparing(StateBank.Node::path));
        parameters.sort(java.util.Comparator.comparing(StateBank.Parameter::key));
        return new Mapping(new StateBank.Layout(nodes, parameters), Map.copyOf(keys));
    }

    private static String remap(String path, Map<String, String> replacements) {
        String prefix = path;
        while (true) {
            String replacement = replacements.get(prefix);
            if (replacement != null) return replacement + path.substring(prefix.length());
            int separator = prefix.lastIndexOf('/');
            if (separator < 0) return path;
            prefix = prefix.substring(0, separator);
        }
    }
}
