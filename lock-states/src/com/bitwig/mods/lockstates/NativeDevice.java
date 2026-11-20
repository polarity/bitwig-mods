package com.bitwig.mods.lockstates;

import bitwig.obf.bDH; // native parameter atom
import bitwig.obf.v3U; // parameter supported by controller value targets
import bitwig.obf.VR3; // integer parameter with a declared native integer range
import bitwig.obf.dpN; // Bitwig's UI event loop
import com.bitwig.flt.control_surface.values.ParameterTarget;
import com.bitwig.flt.control_surface.values.RangedValueTarget;
import com.bitwig.flt.control_surface.values.ValueAtomParameterTarget;
import com.bitwig.flt.document.api.document.ApiModificationProcessor;
import com.bitwig.flt.document.core.master.IOJ; // named undo transaction
import com.bitwig.flt.document.core.master.eD; // automation destination for a parameter
import bitwig.obf.xJo; // track owning device and clip playback
import bitwig.obf.YvI; // timeline event: clips and their contents
import bitwig.obf.LEn; // track automation lane container
import com.bitwig.flt.document.core.master.device.AxS; // native device
import com.bitwig.flt.document.core.master.device.FUR; // native replacement/container shell
import com.bitwig.flt.document.core.master.device.SaG; // native module contents
import com.bitwig.flt.document.core.master.device.tad; // native modulator
import com.bitwig.flt.document.core.master.device.xMq; // modulation assignment (excluded)
import com.bitwig.flt.document.core.master.device.xlG; // device in a chain
import com.bitwig.ramona.core.Dgc; // owned object reference
import com.bitwig.ramona.core.OLR; // list element
import com.bitwig.ramona.core.bnq; // owned object list
import com.bitwig.ramona.core.oDc; // document object
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;

/** Bitwig 6.1.4 adapter. All obfuscated references are compile checked and remapped by Patchbay. */
final class NativeDevice {
    record IdentityEdit(oDc object, String before, String after) { }
    record Read(StateBank.Layout layout, List<Recall.Target> targets, StateBank.Layout legacyLayout,
                Map<String, String> legacyKeys, List<IdentityEdit> identities) {
        StateBank upgrade(StateBank bank) { return StateMigration.upgrade(bank, legacyLayout, layout, legacyKeys); }
    }
    private record Child(String path, oDc object) { }
    private static final int MAX_OBJECTS = 262144; // visited document objects, not parameters

    static void later(Runnable work) { dpN.TVZ().asyncExec(work); } // finish the current UI event first

    static boolean supported(Object device) { return device instanceof AxS || device instanceof FUR; }

    static xlG owner(xlG selected) {
        xlG owner = selected;
        Set<oDc> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (oDc ancestor = selected; ancestor != null; ancestor = ancestor.K_()) { // follow actual ownership, never adjacent track devices
            if (!seen.add(ancestor) || seen.size() > 256) throw new IllegalArgumentException("Invalid device ownership tree");
            if (ancestor instanceof xlG device) owner = device;
        }
        return owner;
    }

    static xJo track(xlG device) {
        for (oDc current = device; current != null; current = current.K_()) if (current instanceof xJo track) return track;
        return null;
    }

    static List<xlG> roots(xJo track) {
        var result = new ArrayList<xlG>();
        Set<oDc> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        roots(track, seen, result, 0);
        return List.copyOf(result);
    }

    private static void roots(oDc object, Set<oDc> seen, List<xlG> result, int depth) {
        // Clips, notes and automation lanes never own devices. Skipping them keeps the walk
        // proportional to the device chain instead of the track's entire timeline content,
        // which includes this mod's own generated lanes and clips.
        if (object instanceof YvI || object instanceof LEn) return;
        if (!seen.add(object)) return;
        if (depth > 64 || seen.size() > 65536) throw new IllegalArgumentException("Track tree exceeds supported size");
        if (object instanceof xlG device) { result.add(device); return; }
        for (Object field : object.upC()) if (field instanceof bnq list) for (int i = 0; i < list.MRY(); i++) {
            if (list.Hnt(i) instanceof oDc child && child.K_() == object) roots(child, seen, result, depth + 1);
        }
        for (Object field : object.CXm()) if (field instanceof Dgc reference && reference.getValue() instanceof oDc child && child.K_() == object) roots(child, seen, result, depth + 1);
    }

    private static NativeTarget nativeTarget(Recall.Target target) {
        if (target instanceof MappedTarget mapped) return nativeTarget(mapped.target());
        if (target instanceof NativeTarget nativeTarget) return nativeTarget;
        throw new IllegalArgumentException("Expected a native parameter target");
    }
    static eD automation(Recall.Target target) { return eD.cfM((bDH) nativeTarget(target).atom); }
    static double raw(Recall.Target target, double normalized) {
        NativeTarget nativeTarget = nativeTarget(target);
        return nativeTarget.integerRange == null ? nativeTarget.value.normalizedValueToValue(normalized) : nativeTarget.integerRange.raw(normalized);
    }
    static void restoreAutomation(Recall.Target target) { nativeTarget(target).target.restoreAutomationControl(); }
    static void resumeNoteAutomation(xlG owner) {
        for (var target : read(owner).targets()) if (NativeSequence.managedOnly((bDH) nativeTarget(target).atom)) restoreAutomation(target);
    }

    static Read read(xlG root) { return read(root, false); }

    static Read read(xlG root, boolean capturing) {
        var nodes = new ArrayList<StateBank.Node>();
        var targets = new ArrayList<Recall.Target>();
        Set<oDc> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<v3U> parameters = Collections.newSetFromMap(new IdentityHashMap<>());
        var devices = new ArrayList<xlG>();
        var anchors = new LinkedHashMap<String, com.bitwig.flt.document.core.master.device.Dgc>();
        walk("root", root, 0, seen, parameters, devices, nodes, targets, anchors, capturing);
        // Cross-check the documented direct-parameter path against the owned-object walk.
        for (xlG device : devices) {
            String[] ids = device.qm(); // all direct parameter IDs, as used by DeviceProxy
            if (ids == null) throw new IllegalArgumentException("The device did not provide parameter IDs");
            for (String id : ids) {
                v3U atom = device.MRY(id); // resolve a direct parameter ID
                if (atom == null || !parameters.contains(atom)) {
                    throw new IllegalArgumentException("Uncovered native parameter: " + id);
                }
            }
        }
        targets.sort(java.util.Comparator.comparing(t -> t.descriptor().key()));
        nodes.sort(java.util.Comparator.comparing(StateBank.Node::path));
        var legacy = new StateBank.Layout(nodes, targets.stream().map(Recall.Target::descriptor).toList());
        var entries = anchors.entrySet().stream().filter(entry -> !entry.getKey().equals("root")).toList();
        var sources = entries.stream().map(entry -> new DeviceIdentity.Source(NativeStorage.identity(entry.getValue()),
                entry.getValue().kFU().toString())).toList(); // native UUID distinguishes a copied child from its source
        var planned = DeviceIdentity.plan(sources);
        var tokens = new HashMap<String, String>();
        var edits = new ArrayList<IdentityEdit>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i); var identity = planned.get(i);
            tokens.put(entry.getKey(), identity.token());
            if (!identity.stored().equals(sources.get(i).stored())) edits.add(new IdentityEdit(entry.getValue(), sources.get(i).stored(), identity.stored()));
        }
        var mapping = SnapshotPaths.map(legacy, tokens);
        var descriptors = new HashMap<String, StateBank.Parameter>();
        mapping.layout().parameters().forEach(parameter -> descriptors.put(parameter.key(), parameter));
        var stableTargets = new ArrayList<Recall.Target>();
        for (Recall.Target target : targets) {
            stableTargets.add(new MappedTarget(target, descriptors.get(mapping.keys().get(target.descriptor().key()))));
        }
        stableTargets.sort(java.util.Comparator.comparing(target -> target.descriptor().key()));
        return new Read(mapping.layout(), List.copyOf(stableTargets), legacy, mapping.keys(), List.copyOf(edits));
    }

    static void withIdentities(Read read, Runnable edit) {
        var applied = new ArrayList<IdentityEdit>();
        try {
            for (var identity : read.identities()) {
                if (!NativeStorage.identity(identity.object()).equals(identity.before())) throw new IllegalArgumentException("The device identity changed during this operation");
                applied.add(identity);
                NativeStorage.writeIdentity(identity.object(), identity.after());
            }
            edit.run();
        } catch (RuntimeException | LinkageError failure) {
            for (int i = applied.size() - 1; i >= 0; i--) {
                var identity = applied.get(i);
                try { NativeStorage.writeIdentity(identity.object(), identity.before()); }
                catch (RuntimeException | LinkageError rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }

    private record MappedTarget(Recall.Target target, StateBank.Parameter descriptor) implements Recall.Target {
        public boolean writable() { return target.writable(); }
        public boolean automated() { return target.automated(); }
        public double read() { return target.read(); }
        public void write(double value) { target.write(value); }
    }

    private static void walk(String path, oDc object, int depth, Set<oDc> seen, Set<v3U> parameters,
                             List<xlG> devices, List<StateBank.Node> nodes, List<Recall.Target> targets,
                             Map<String, com.bitwig.flt.document.core.master.device.Dgc> anchors, boolean capturing) {
        // Bound the walk by visited objects; the parameter limit is checked where parameters are counted.
        if (depth > 64 || seen.size() >= MAX_OBJECTS) {
            throw new IllegalArgumentException("The device structure exceeds the supported size");
        }
        if (object instanceof xMq || !seen.add(object)) return; // never visit modulation depths or destinations
        if (object instanceof xlG device) {
            if (!supported(device)) {
                if (capturing || depth == 0) throw new IllegalArgumentException("Only native devices are supported: " + device.aG_());
                return; // a new unsupported effect is outside old native snapshots; do not touch its subtree
            }
            nodes.add(new StateBank.Node(path, "device:" + device.qoU().cfM())); // native device type, not instance ID
            devices.add(device);
            anchors.put(path, device);
        } else if (object instanceof tad modulator) {
            nodes.add(new StateBank.Node(path, "modulator:" + modulator.qoU().cfM()));
            anchors.put(path, modulator);
        } else if (object instanceof SaG module) {
            nodes.add(new StateBank.Node(path, "module:" + module.UNj().cfM())); // native module type ID
        }
        if (object instanceof v3U && !(object instanceof bDH)) {
            throw new IllegalArgumentException("Unsupported native parameter representation at " + path);
        }
        if (object instanceof bDH atom) {
            ParameterTarget target = ValueAtomParameterTarget.createForAtom(atom);
            if (target == null || target.getValueTarget() == null) {
                throw new IllegalArgumentException("Unsupported native parameter: " + atom.fFs());
            }
            String key = path + "/parameter/" + segment(atom.ay_()); // stable parameter ID
            if (targets.size() >= StateBank.MAX_PARAMETERS) {
                throw new IllegalArgumentException("The device has more than " + StateBank.MAX_PARAMETERS + " parameters");
            }
            targets.add(new NativeTarget(key, atom, target));
            parameters.add(atom);
            return; // automation, expression and modulation metadata are not part of a value snapshot
        }
        var children = new ArrayList<Child>();
        for (Object field : object.upC()) { // the object's lists
            if (!(field instanceof bnq list)) throw new IllegalArgumentException("Unknown native list");
            for (int i = 0; i < list.MRY(); i++) { // list size
                OLR item = list.Hnt(i); // element
                if (item instanceof oDc child && child.K_() == object) { // owned here, not a cross-reference
                    children.add(new Child(path + "/list/" + segment(list.E3()) + "/" + i, child));
                }
            }
        }
        for (Object field : object.CXm()) { // the object's references
            if (!(field instanceof Dgc reference)) throw new IllegalArgumentException("Unknown native reference");
            if (reference.getValue() instanceof oDc child && child.K_() == object) {
                children.add(new Child(path + "/ref/" + segment(reference.E3()), child));
            }
        }
        children.sort(java.util.Comparator.comparing(Child::path));
        for (Child child : children) walk(child.path, child.object, depth + 1, seen, parameters, devices, nodes, targets, anchors, capturing);
    }

    private static String segment(String text) {
        StateBank.text(text);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static boolean automationWriting(xlG device) {
        var project = device.apV(); // owning project
        if (project == null || project.ifN() == null) throw new IllegalArgumentException("The project is not available");
        return project.ifN().NMA().getBoolValue(); // native transport automation-write flag
    }

    static void transaction(xlG device, String label, Runnable edit) {
        transaction((oDc) device, label, edit);
    }

    static void transaction(oDc device, String label, Runnable edit) {
        IOJ change = new IOJ();
        if (!change.cfM(device, label)) throw new IllegalStateException("Bitwig refused the Undo step");
        Throwable[] failure = {null};
        try {
            ApiModificationProcessor.modify(() -> {
                try { edit.run(); }
                catch (RuntimeException | LinkageError error) { failure[0] = error; }
            });
        } finally {
            if (!change.MRY()) { // finish the named change, including any rollback
                throw new IllegalStateException("Bitwig refused to finish the Undo step", failure[0]);
            }
        }
        if (failure[0] instanceof RuntimeException error) throw error;
        if (failure[0] instanceof LinkageError error) throw error;
    }

    private static final class NativeTarget implements Recall.Target {
        private final v3U atom;
        private final ParameterTarget target;
        private final RangedValueTarget value;
        private final StateBank.Parameter descriptor;
        private final IntegerRange integerRange;

        NativeTarget(String key, bDH atom, ParameterTarget target) {
            this.atom = atom; this.target = target; value = target.getValueTarget();
            if (atom instanceof VR3 integer) {
                var range = integer.Grk(); // native integer range, independent of controller normalization
                integerRange = new IntegerRange(range.UNj(), range.TMF()); // declared minimum and maximum
            } else integerRange = null;
            String[] choices = value.discreteValueNames();
            String name = atom.fFs(); // display name; internal controls can have none
            if (name == null || name.isBlank()) name = atom.ay_(); // stable ID as a diagnostic label
            descriptor = new StateBank.Parameter(key, name, value.isContinuous() ? "continuous" : "discrete",
                    integerRange != null ? integerRange.steps() : value.isContinuous() ? 0 : value.discreteValueCount(),
                    integerRange != null ? integerRange.minimum() : value.normalizedValueToValue(0),
                    integerRange != null ? integerRange.maximum() : value.normalizedValueToValue(1),
                    choices == null ? List.of() : List.of(choices));
        }

        @Override public StateBank.Parameter descriptor() { return descriptor; }
        @Override public boolean writable() { return atom.d3() && value.isSettable(); } // may be written now
        @Override public boolean automated() {
            if (target.getHasAutomationTarget() == null) throw new IllegalArgumentException("Automation status is unavailable");
            return target.getHasAutomationTarget().getBoolValue() && !NativeSequence.managedOnly((bDH) atom);
        }
        @Override public double read() { // base value, never modulated value
            return integerRange == null ? value.getCurrentNormalizedValue() : integerRange.normalized(value.getCurrentValue());
        }
        @Override public void write(double normalized) {
            target.startedAdjusting(false, true);
            try { value.setCurrentValue(integerRange == null ? value.normalizedValueToValue(normalized) : integerRange.raw(normalized)); }
            finally { target.finishedAdjusting(false, true); }
        }
    }
}
