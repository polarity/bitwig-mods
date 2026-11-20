package com.bitwig.mods.lockstates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StateTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("read-bank")) {
            StateBank bank = new BankStore(Path.of(args[1])).load(args[2]);
            if (!bank.name().equals("Polymer & Reverb") || !bank.states().getFirst().name().equals("Cutoff ü / 短")) {
                throw new AssertionError("Bank changed across processes");
            }
            System.out.println("CROSS_PROCESS_BANK_OK");
            return;
        }
        snapshotsAndRecall();
        refusalBeforeWrites();
        rollbackAndVerification();
        persistenceAndMalformedInput();
        wideIntegerRanges();
        orderedChoiceRanges();
        instancePayloads();
        flexibleRecall();
        stableIdentities();
        legacyMigration();
        stablePaths();
        noteSequences();
        System.out.println("LOCK_STATES_TESTS_OK checks=" + checks);
    }

    private static void noteSequences() {
        String owner = UUID.randomUUID().toString(), otherOwner = UUID.randomUUID().toString();
        var p = flexibleTarget("root/contents/parameter/cutoff", 0.2).descriptor();
        var fx = flexibleTarget("device/A/parameter/time", 0.3).descriptor();
        var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "device:instrument"), new StateBank.Node("device/A", "device:delay")), List.of(p, fx));
        var a = StateBank.capture("A", Map.of(p.key(), 0.2, fx.key(), 0.3), layout);
        var b = StateBank.capture("B", Map.of(p.key(), 0.8, fx.key(), 0.9), layout);
        var bank = StateBank.empty("States", layout).withStates(List.of(a, b));
        var bindings = NoteAssignments.empty().with(owner, a.id()).with(otherOwner, b.id());
        check(NoteAssignments.decode(bindings.encode()).equals(bindings), "one note retains independent parent-device assignments");
        check(bindings.with(owner, null).states().equals(Map.of(otherOwner, b.id())), "unbinding one parent preserves all others");
        check(NoteAssignments.decode("").states().isEmpty() && bindings.with(owner, null).with(otherOwner, null).encode().isEmpty(), "unassigned notes use empty native metadata");
        fails(() -> new NoteAssignments(2, Map.of()));
        fails(() -> NoteAssignments.empty().with("bad-owner", a.id()));
        fails(() -> NoteAssignments.decode("{broken"));
        fails(() -> NoteAssignments.decode("null"));
        var spec = new CurveSpec(1, owner, p, 0.4, 0.4);
        check(CurveSpec.decode(spec.encode()).equals(spec), "curve metadata retains stable owner, parameter and baseline");
        fails(() -> new CurveSpec(1, owner, p, Double.NaN, 0));
        fails(() -> new CurveSpec(1, owner, p, -0.1, 0));
        fails(() -> new CurveSpec(1, owner, p, 1.1, 0));
        fails(() -> CurveSpec.decode("{}"));
        var events = List.of(new SequencePlan.Event(3, b.id()), new SequencePlan.Event(1, a.id()), new SequencePlan.Event(1, a.id()));
        var program = SequencePlan.compile(events, bank, layout);
        check(program.points(p.key(), 0.5).equals(List.of(new SequencePlan.Point(0, 0.5), new SequencePlan.Point(1, 0.2), new SequencePlan.Point(3, 0.8))), "events sort, identical-state chords deduplicate, baseline precedes first assigned note");
        check(program.points(fx.key(), 0.4).equals(List.of(new SequencePlan.Point(0, 0.4), new SequencePlan.Point(1, 0.3), new SequencePlan.Point(3, 0.9))), "all captured compatible controls follow the same note positions");
        check(SequencePlan.compile(List.of(new SequencePlan.Event(0, a.id())), bank, layout).points(p.key(), 0.5).equals(List.of(new SequencePlan.Point(0, 0.2))), "a bound note at zero overrides the baseline");
        check(SequencePlan.compile(List.of(), bank, layout).points(p.key(), 0.5).isEmpty(), "unassigned notes never reset a device");
        check(SequencePlan.compile(List.of(new SequencePlan.Event(1, UUID.randomUUID().toString())), bank, layout).changes().isEmpty(), "deleted states are inactive bindings");
        fails(() -> SequencePlan.compile(List.of(new SequencePlan.Event(1, a.id()), new SequencePlan.Event(1, b.id())), bank, layout));
        fails(() -> new SequencePlan.Event(-1, a.id()));
        fails(() -> new SequencePlan.Event(Double.NaN, a.id()));
        var extra = flexibleTarget("device/B/parameter/time", 0.7).descriptor();
        var changed = new StateBank.Layout(List.of(new StateBank.Node("root", "device:instrument"), new StateBank.Node("device/B", "device:delay")), List.of(p, extra));
        var surviving = SequencePlan.compile(events, bank, changed);
        check(surviving.changes().keySet().equals(java.util.Set.of(p.key())), "removed effects are skipped and new effects remain untouched in note sequences");
        var replaced = new StateBank.Layout(List.of(new StateBank.Node("root", "device:other")), List.of(p));
        check(SequencePlan.compile(events, bank, replaced).changes().isEmpty(), "replaced parent types cannot inherit old sequences");
        var changedRange = new StateBank.Parameter(p.key(), p.name(), "continuous", 0, -1, 1, List.of());
        var incompatible = new StateBank.Layout(layout.nodes(), List.of(changedRange, fx));
        check(!SequencePlan.compile(events, bank, incompatible).changes().containsKey(p.key()), "incompatible parameter ranges are skipped");
        var legacy = new DeviceStates(1, bank, a.id());
        check(!DeviceStates.decode(legacy.encode()).notePlayback(), "old project payloads default to note playback disabled");
        var enabled = legacy.playback(true);
        check(enabled.version() == DeviceStates.VERSION && DeviceStates.decode(enabled.encode()).notePlayback(), "enabling note playback upgrades and persists the device payload");
        check(enabled.with(bank, b.id()).notePlayback() && !enabled.playback(false).notePlayback(), "state selection retains playback mode and disabling is explicit");
    }

    private static void stablePaths() {
        String slotA = "root/fx/list/0", slotB = "root/fx/list/1";
        var a = flexibleTarget(slotA + "/contents/parameter/time", 0.3);
        var b = flexibleTarget(slotB + "/contents/parameter/time", 0.4);
        var root = flexibleTarget("root/contents/parameter/cutoff", 0.2);
        var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "device:instrument"),
                new StateBank.Node("root/contents", "module:synth"), new StateBank.Node(slotA, "device:delay"),
                new StateBank.Node(slotA + "/contents", "module:delay"), new StateBank.Node(slotB, "device:delay"),
                new StateBank.Node(slotB + "/contents", "module:delay")), List.of(root.descriptor, a.descriptor, b.descriptor));
        var mapping = SnapshotPaths.map(layout, Map.of(slotA, "A", slotB, "B"));
        check(mapping.keys().get(a.descriptor.key()).startsWith("device/A/"), "device identities replace all preceding list positions");
        check(mapping.keys().get(b.descriptor.key()).startsWith("device/B/"), "identical effects retain distinct paths");
        var shifted = new StateBank.Layout(List.of(new StateBank.Node("root", "device:instrument"), new StateBank.Node("root/contents", "module:synth"),
                new StateBank.Node(slotA, "device:delay"), new StateBank.Node(slotA + "/contents", "module:delay")),
                List.of(root.descriptor, flexibleTarget(slotA + "/contents/parameter/time", 0.4).descriptor));
        var shiftedMapping = SnapshotPaths.map(shifted, Map.of(slotA, "B"));
        check(shiftedMapping.keys().get(slotA + "/contents/parameter/time").equals(mapping.keys().get(b.descriptor.key())), "removing the preceding effect keeps survivor parameter keys unchanged");
        check(mapping.keys().get(root.descriptor.key()).equals(shiftedMapping.keys().get(root.descriptor.key())), "parent parameter paths stay stable across chain edits");
        var otherModuleNodes = new ArrayList<>(shifted.nodes());
        otherModuleNodes.set(3, new StateBank.Node(slotA + "/contents", "module:other-delay"));
        var moduleChanged = SnapshotPaths.map(new StateBank.Layout(otherModuleNodes, shifted.parameters()), Map.of(slotA, "B"));
        check(!moduleChanged.keys().get(slotA + "/contents/parameter/time").equals(mapping.keys().get(b.descriptor.key())), "replaced module types cannot reuse old parameter keys");
        fails(() -> SnapshotPaths.map(layout, Map.of(slotA, "same", slotB, "same")));
    }

    private static Fake flexibleTarget(String key, double value) {
        return new Fake(new StateBank.Parameter(key, "Control", "continuous", 0, 0, 1, List.of()), value);
    }

    private static StateBank.Layout flexibleLayout(List<Fake> targets, String... devices) {
        var nodes = new ArrayList<StateBank.Node>();
        nodes.add(new StateBank.Node("root", "device:instrument"));
        for (String device : devices) nodes.add(new StateBank.Node("device/" + device, "device:delay"));
        return new StateBank.Layout(nodes, targets.stream().map(Fake::descriptor).toList());
    }

    private static void flexibleRecall() throws Exception {
        var root = flexibleTarget("root/parameter/cutoff", 0.2);
        var a = flexibleTarget("device/A/parameter/time", 0.3);
        var b = flexibleTarget("device/B/parameter/time", 0.4);
        var initial = List.of(root, a, b);
        var original = flexibleLayout(initial, "A", "B");
        var state = StateBank.capture("Before changes", Recall.capture(initial), original);
        var bank = StateBank.empty("Owner", original).withStates(List.of(state));
        var c = flexibleTarget("device/C/parameter/time", Double.NaN);
        c.automated = true; c.writable = false;
        var current = List.of(b, root, c); // A removed; identical-type B moved; identical-type C inserted
        var layout = flexibleLayout(current, "C", "B");
        root.value = 0.9; b.value = 0.8;
        var result = Recall.apply(bank, state, layout, current, false, (label, edit) -> edit.run());
        check(root.value == 0.2 && b.value == 0.4, "surviving parent and reordered effect restore by identity");
        check(c.writes == 0 && Double.isNaN(c.value), "new effect is never read or written, even if automated or read-only");
        check(result.applied() == 2 && result.skipped() == 1 && result.untouched() == 1, "partial recall reports applied/skipped/untouched counts");
        check(a.writes == 0 && state.values().containsKey(a.descriptor.key()), "removed devices remain stored in old states");
        var replacement = flexibleTarget("device/replacement/parameter/time", 0.99);
        root.value = 0.7; b.value = 0.7;
        var replaced = List.of(root, b, replacement);
        Recall.apply(bank, state, flexibleLayout(replaced, "B", "replacement"), replaced, false, (label, edit) -> edit.run());
        check(replacement.writes == 0 && replacement.value == 0.99 && b.value == 0.4, "same-type replacements cannot inherit a removed instance's values");

        c.value = 0.6; c.writable = true; c.automated = false;
        var after = StateBank.capture("After insert", Recall.capture(current), layout);
        var mixed = bank.withStates(List.of(state, after));
        mixed = BankStore.decode(BankStore.encode(mixed));
        check(mixed.states().getFirst().layout().equals(original) && mixed.states().getLast().layout().equals(layout), "each state persists its own structure");
        root.value = 0.8; b.value = 0.8; c.value = 0.8;
        Recall.apply(mixed, mixed.states().getLast(), layout, current, false, (label, edit) -> edit.run());
        check(c.value == 0.6, "a newer state's captured effect is restored");
        c.value = 0.91;
        Recall.apply(mixed, mixed.states().getFirst(), layout, current, false, (label, edit) -> edit.run());
        check(c.value == 0.91, "older states still leave the later effect unchanged");

        root.value = 0.85; b.value = 0.75;
        int cWrites = c.writes;
        fails(() -> Recall.apply(bank, state, layout, current, false, (label, edit) -> edit.run(), () -> { throw new IllegalStateException("metadata failed"); }));
        check(root.value == 0.85 && b.value == 0.75 && c.writes == cWrites, "partial recall rollback touches only matched parameters");
        int rootWrites = root.writes;
        b.automated = true;
        fails(() -> Recall.apply(bank, state, layout, current, false, (label, edit) -> { throw new AssertionError("must validate first"); }));
        check(root.writes == rootWrites, "automation on a matched survivor refuses the whole recall before writes");
        b.automated = false;
        var wrongRoot = new StateBank.Layout(List.of(new StateBank.Node("root", "device:other")), layout.parameters());
        fails(() -> Recall.apply(bank, state, wrongRoot, current, false, (label, edit) -> { throw new AssertionError("wrong owner"); }));
        var noMatches = List.of(replacement);
        fails(() -> Recall.apply(bank, state, flexibleLayout(noMatches, "replacement"), noMatches, false, (label, edit) -> { throw new AssertionError("empty recall"); }));
        var differentType = new StateBank.Layout(List.of(new StateBank.Node("root", "device:instrument"), new StateBank.Node("device/B", "device:other-effect")), layout.parameters());
        b.value = 0.88;
        result = Recall.apply(bank, state, differentType, current, false, (label, edit) -> edit.run());
        check(b.value == 0.88 && result.applied() == 1, "matching a key alone cannot cross an incompatible device type");
        var incompatible = new Fake(new StateBank.Parameter(b.descriptor.key(), "Different range", "continuous", 0, -1, 1, List.of()), 0.66);
        var changedRange = List.of(root, incompatible);
        result = Recall.apply(bank, state, flexibleLayout(changedRange, "B"), changedRange, false, (label, edit) -> edit.run());
        check(incompatible.writes == 0 && result.applied() == 1, "incompatible surviving parameter ranges are skipped");
        var updated = new StateBank.State(state.id(), state.name(), after.values(), after.layout());
        var untouched = new StateBank.State(UUID.randomUUID().toString(), "Unchanged", state.values(), state.layout());
        var updatedBank = bank.withStates(List.of(updated, untouched));
        check(updatedBank.layoutOf(updated).equals(layout) && updatedBank.layoutOf(untouched).equals(original), "explicit updates change only that state's scope");
    }

    private static void stableIdentities() {
        String first = UUID.randomUUID().toString(); String second = UUID.randomUUID().toString();
        var initial = DeviceIdentity.plan(List.of(new DeviceIdentity.Source("", first), new DeviceIdentity.Source("", second)));
        check(!initial.getFirst().token().equals(initial.getLast().token()), "new identical effects receive different tokens");
        var reordered = DeviceIdentity.plan(List.of(new DeviceIdentity.Source(initial.getLast().stored(), second), new DeviceIdentity.Source(initial.getFirst().stored(), first)));
        check(reordered.getFirst().token().equals(initial.getLast().token()), "tokens survive list reorder");
        String clonedNative = UUID.randomUUID().toString();
        var clone = DeviceIdentity.plan(List.of(new DeviceIdentity.Source(initial.getFirst().stored(), clonedNative))).getFirst();
        check(clone.token().equals(initial.getFirst().token()) && clone.stored().endsWith(clonedNative), "whole-tree copies keep relative tokens and rebase native UUIDs");
        var childCopy = DeviceIdentity.plan(List.of(new DeviceIdentity.Source(initial.getFirst().stored(), first), new DeviceIdentity.Source(initial.getFirst().stored(), clonedNative)));
        check(childCopy.getFirst().token().equals(initial.getFirst().token()), "copy inside owner preserves source identity");
        check(!childCopy.getLast().token().equals(initial.getFirst().token()), "copy inside owner gets a new identity");
        var childCopyInClone = DeviceIdentity.plan(List.of(new DeviceIdentity.Source(clone.stored(), clonedNative), new DeviceIdentity.Source(clone.stored(), second)));
        check(childCopyInClone.getFirst().token().equals(clone.token()) && !childCopyInClone.getLast().token().equals(clone.token()), "rebased whole copies can later duplicate their children independently");
        fails(() -> DeviceIdentity.plan(List.of(new DeviceIdentity.Source(initial.getFirst().stored(), clonedNative), new DeviceIdentity.Source(initial.getFirst().stored(), second))));
        fails(() -> DeviceIdentity.plan(List.of(new DeviceIdentity.Source(initial.getFirst().stored(), first), new DeviceIdentity.Source(initial.getFirst().stored(), first))));
        fails(() -> DeviceIdentity.plan(List.of(new DeviceIdentity.Source("broken", first))));
        fails(() -> DeviceIdentity.plan(List.of(new DeviceIdentity.Source("", "broken"))));
    }

    private static void legacyMigration() throws Exception {
        var targets = targets(24); var legacy = layout(targets);
        var state = StateBank.capture("Legacy", Recall.capture(targets));
        var old = new StateBank(1, UUID.randomUUID().toString(), "Device States", legacy, List.of(state));
        old = BankStore.decode(BankStore.encode(old));
        check(old.formatVersion() == 1 && old.states().getFirst().layout() == null, "format-1 project payloads remain readable");
        var keys = new LinkedHashMap<String, String>();
        var stableParameters = legacy.parameters().stream().map(parameter -> {
            String key = "root/stable/" + parameter.key(); keys.put(parameter.key(), key);
            return new StateBank.Parameter(key, parameter.name(), parameter.kind(), parameter.steps(), parameter.minimum(), parameter.maximum(), parameter.choices());
        }).toList();
        var stable = new StateBank.Layout(legacy.nodes(), stableParameters);
        var upgraded = StateMigration.upgrade(old, legacy, stable, keys);
        check(upgraded.formatVersion() == StateBank.FORMAT && upgraded.states().getFirst().layout().equals(stable), "legacy snapshots acquire per-state layouts");
        check(upgraded.states().getFirst().id().equals(state.id()) && upgraded.states().getFirst().name().equals(state.name()), "migration retains state IDs and names");
        for (var entry : keys.entrySet()) check(upgraded.states().getFirst().values().get(entry.getValue()).equals(state.values().get(entry.getKey())), "migration retains exact values");
        check(BankStore.decode(BankStore.encode(upgraded)).equals(upgraded), "migrated payload survives JSON roundtrip");
        check(StateMigration.upgrade(upgraded, legacy, stable, Map.of()).equals(upgraded), "already migrated states need no positional mapping");
        var changed = new StateBank.Layout(List.of(new StateBank.Node("root", "different")), legacy.parameters());
        StateBank savedOld = old;
        fails(() -> StateMigration.upgrade(savedOld, changed, stable, keys));
        var incomplete = new LinkedHashMap<>(keys); incomplete.remove(incomplete.keySet().iterator().next());
        fails(() -> StateMigration.upgrade(savedOld, legacy, stable, incomplete));
        check(savedOld.states().getFirst().layout() == null && savedOld.states().getFirst().values().equals(state.values()), "failed migration preserves the original data");
    }

    private static void instancePayloads() throws Exception {
        var targets = targets(24);
        var layout = layout(targets);
        var empty = DeviceStates.empty(layout);
        check(empty.selected().isEmpty() && empty.snapshots().states().isEmpty(), "new instance starts without states");
        var first = StateBank.capture("First ü / 短", Recall.capture(targets));
        var data = empty.with(empty.snapshots().withStates(List.of(first)), first.id());
        String saved = data.encode();
        check(DeviceStates.decode(saved).equals(data), "instance payload roundtrip includes selection");
        var second = StateBank.capture("Second", Recall.capture(targets));
        var copy = DeviceStates.decode(saved);
        copy = copy.with(copy.snapshots().withStates(List.of(first, second)), second.id());
        check(data.snapshots().states().size() == 1 && data.selected().equals(first.id()), "duplicate edits do not affect source data");
        check(copy.snapshots().states().size() == 2 && copy.selected().equals(second.id()), "duplicate can keep an independent selection");
        check(DeviceStates.decode(copy.encode()).equals(copy), "updated instance payload preserves state order");
        check(DeviceStates.empty(layout).snapshots().states().isEmpty(), "another instance does not inherit earlier states");
        fails(() -> new DeviceStates(999, data.snapshots(), data.selected()));
        fails(() -> new DeviceStates(1, data.snapshots(), UUID.randomUUID().toString()));
        fails(() -> DeviceStates.decode(saved.replace("\"version\": " + DeviceStates.VERSION, "\"version\": 999")));
        fails(() -> DeviceStates.decode("{\"version\":1,\"version\":1}"));
        fails(() -> DeviceStates.decode("{broken"));
        fails(() -> DeviceStates.decode(saved + "{}"));
        fails(() -> DeviceStates.decode("{}"));
        fails(() -> DeviceStates.decode("null"));

        for (Fake target : targets) target.value = target.descriptor.kind().equals("continuous") ? 0.9 : 1;
        var before = Recall.capture(targets);
        int[] commits = {0};
        fails(() -> Recall.apply(data.snapshots(), first, layout, targets, false, (label, edit) -> edit.run(), () -> {
            commits[0]++; throw new IllegalStateException("Cannot persist the selected state");
        }));
        check(commits[0] == 1 && Recall.capture(targets).equals(before), "metadata commit failure rolls parameter values back");
        targets.getFirst().automated = true;
        fails(() -> Recall.apply(data.snapshots(), first, layout, targets, false, (label, edit) -> edit.run(), () -> commits[0]++));
        check(commits[0] == 1, "refused recall never changes the selected state");
    }

    private static void orderedChoiceRanges() throws Exception {
        for (List<Double> rawValues : List.of(List.of(0.0, -1.0, -2.0), List.of(0.0, -12.0, -24.0),
                List.of(10.0, -10.0, 0.0), List.of(-2.0, -1.0, 0.0))) {
            var descriptor = new StateBank.Parameter("OCTAVE", "Octave", "discrete", rawValues.size(),
                    rawValues.getFirst(), rawValues.getLast(), List.of("First", "Second", "Third"));
            var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "native-choice-test")), List.of(descriptor));
            StateBank bank = StateBank.empty("Octave choices", layout);
            var target = new ChoiceTarget(descriptor, rawValues);
            var states = new ArrayList<StateBank.State>();
            for (int index = 0; index < rawValues.size(); index++) {
                target.raw = rawValues.get(index);
                var state = StateBank.capture("Choice " + index, Recall.capture(List.of(target)));
                check(state.values().get("OCTAVE") == index / 2.0, "capture preserves native choice order");
                states.add(state);
            }
            bank = bank.withStates(states);
            bank = BankStore.decode(BankStore.encode(bank));
            check(bank.layout().parameters().getFirst().minimum() == rawValues.getFirst()
                    && bank.layout().parameters().getFirst().maximum() == rawValues.getLast(), "JSON preserves endpoint direction");
            for (int index = 0; index < states.size(); index++) {
                target.raw = rawValues.get((index + 1) % rawValues.size());
                Recall.apply(bank, bank.states().get(index), layout, List.of(target), false, (label, action) -> action.run());
                check(target.raw == rawValues.get(index), "recall restores exact negative or sparse raw choice");
            }
            var reverse = new StateBank.Parameter("OCTAVE", "Octave", "discrete", rawValues.size(),
                    rawValues.getLast(), rawValues.getFirst(), descriptor.choices());
            fails(() -> layout.requireCompatible(new StateBank.Layout(layout.nodes(), List.of(reverse))));
        }
        new StateBank.Parameter("bipolar", "Inverted", "continuous", 0, 1, -1, List.of());
        check(true, "descending continuous native mappings are valid");
        fails(() -> new StateBank.Parameter("bad", "Bad", "discrete", 3, 0, Double.NaN, List.of()));
        fails(() -> new StateBank.Parameter("bad", "Bad", "discrete", 3, Double.NEGATIVE_INFINITY, -2, List.of()));
        fails(() -> new StateBank.Parameter("bad", "Bad", "discrete", 0, 0, -2, List.of()));
    }

    private static final class ChoiceTarget implements Recall.Target {
        final StateBank.Parameter descriptor;
        final List<Double> values;
        double raw;
        ChoiceTarget(StateBank.Parameter descriptor, List<Double> values) {
            this.descriptor = descriptor; this.values = values; raw = values.getFirst();
        }
        public StateBank.Parameter descriptor() { return descriptor; }
        public boolean writable() { return true; }
        public boolean automated() { return false; }
        public double read() { return values.indexOf(raw) / (double) (values.size() - 1); }
        public void write(double normalized) { raw = values.get((int) Math.round(normalized * (values.size() - 1))); }
    }

    private static void wideIntegerRanges() throws Exception {
        for (IntegerRange range : List.of(new IntegerRange(-8, 8), new IntegerRange(Integer.MIN_VALUE, Integer.MAX_VALUE),
                new IntegerRange(0, Integer.MAX_VALUE), new IntegerRange(-1, Integer.MAX_VALUE), new IntegerRange(4, 4))) {
            check(range.steps() > 0, "integer cardinality cannot overflow");
            for (int raw : new int[]{range.minimum(), range.maximum(), 0, -1, 1, -12, 12, -127, 127}) {
                if (raw >= range.minimum() && raw <= range.maximum()) {
                    check(range.raw(range.normalized(raw)) == raw, "exact native integer roundtrip");
                }
            }
            var descriptor = new StateBank.Parameter("OCTAVE", "Octave", "discrete", range.steps(), range.minimum(), range.maximum(), List.of());
            var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "native-test")), List.of(descriptor));
            var values = Map.of("OCTAVE", range.normalized(range.minimum() <= 0 && range.maximum() >= 0 ? 0 : range.minimum()));
            var bank = StateBank.empty("Wide integers", layout).withStates(List.of(StateBank.capture("A", values)));
            check(BankStore.decode(BankStore.encode(bank)).equals(bank), "long step count survives JSON");
        }
        check(new IntegerRange(Integer.MIN_VALUE, Integer.MAX_VALUE).steps() == 4294967296L, "entire signed integer domain");
        fails(() -> new IntegerRange(3, 2));
        fails(() -> new IntegerRange(-8, 8).raw(Double.NaN));
        fails(() -> new IntegerRange(-8, 8).normalized(9));
        fails(() -> new IntegerRange(-8, 8).normalized(0.5));
        var range = new IntegerRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        var descriptor = new StateBank.Parameter("OCTAVE", "Octave", "discrete", range.steps(), range.minimum(), range.maximum(), List.of());
        var target = new Fake(descriptor, 1);
        var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "native-test")), List.of(descriptor));
        var state = StateBank.capture("Zero", Map.of("OCTAVE", range.normalized(0)));
        var bank = StateBank.empty("Wide integer recall", layout).withStates(List.of(state));
        target.offsetOnce = 1.0 / (range.steps() - 1);
        fails(() -> Recall.apply(bank, state, layout, List.of(target), false, (label, action) -> action.run()));
        check(target.value == 1, "even a one-integer readback error triggers rollback on a wide range");
    }

    private static void snapshotsAndRecall() {
        List<Fake> targets = targets(24);
        var layout = layout(targets);
        var captured = StateBank.capture("Warm", Recall.capture(targets));
        var bank = StateBank.empty("Polymer + Reverb", layout).withStates(List.of(captured));
        for (Fake target : targets) target.value = target.descriptor.kind().equals("continuous") ? 0.9 : 1;
        int[] transactions = {0};
        Recall.apply(bank, captured, layout, targets, false, (label, action) -> {
            check(label.equals("Recall Lock State: Warm"), "named undo transaction");
            transactions[0]++; action.run();
        });
        check(transactions[0] == 1, "one transaction for 24 parameters");
        for (Fake target : targets) check(target.value == captured.values().get(target.descriptor.key()), "exact value roundtrip");
        String first = captured.values().keySet().iterator().next();
        targets.getFirst().value = 0.7;
        check(captured.values().get(first) != 0.7, "knob changes do not edit snapshots");
        var renamed = new StateBank.State(captured.id(), "Brighter", captured.values());
        check(renamed.id().equals(captured.id()), "rename preserves ID");
        var identicalSecondDevice = targets(24);
        layout.requireCompatible(layout(identicalSecondDevice));
        check(true, "compatible second instance");
        var translated = layout.parameters().stream().map(p -> new StateBank.Parameter(p.key(), "Anderer Name",
                p.kind(), p.steps(), p.minimum(), p.maximum(), p.choices().isEmpty() ? List.of() : List.of("Aus", "Eins", "Zwei"))).toList();
        layout.requireCompatible(new StateBank.Layout(layout.nodes(), translated));
        check(true, "display labels do not decide compatibility");
        try { captured.values().put("x", 1.0); throw new AssertionError("mutable snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
    }

    private static void refusalBeforeWrites() {
        List<Fake> targets = targets(24);
        var layout = layout(targets);
        var state = StateBank.capture("A", Recall.capture(targets));
        var bank = StateBank.empty("Bank", layout).withStates(List.of(state));
        Recall.Transaction forbidden = (label, action) -> { throw new AssertionError("transaction must not start"); };
        targets.get(20).automated = true;
        fails(() -> Recall.apply(bank, state, layout, targets, false, forbidden));
        targets.get(20).automated = false;
        fails(() -> Recall.apply(bank, state, layout, targets, true, forbidden));
        targets.get(20).writable = false;
        fails(() -> Recall.apply(bank, state, layout, targets, false, forbidden));
        targets.get(20).writable = true;
        var changed = new StateBank.Layout(List.of(new StateBank.Node("root", "different-device")), layout.parameters());
        fails(() -> Recall.apply(bank, state, changed, targets, false, forbidden));
        var incomplete = new StateBank.State(UUID.randomUUID().toString(), "Bad", Map.of("unknown", 0.2));
        fails(() -> Recall.apply(bank, incomplete, layout, targets, false, forbidden));
        var missing = new ArrayList<Fake>(targets); missing.removeLast();
        fails(() -> Recall.apply(bank, state, layout, missing, false, forbidden));
        var changedParameters = new ArrayList<>(layout.parameters());
        var old = changedParameters.getFirst();
        changedParameters.set(0, new StateBank.Parameter("other-parameter-id", old.name(), old.kind(), old.steps(), old.minimum(), old.maximum(), old.choices()));
        var changedIds = new StateBank.Layout(layout.nodes(), changedParameters);
        fails(() -> Recall.apply(bank, state, changedIds, targets, false, forbidden));
        fails(() -> new StateBank.Layout(layout.nodes(), List.of(old, old)));
        check(targets.stream().allMatch(target -> target.writes == 0), "all refusals are read only");
    }

    private static void rollbackAndVerification() {
        List<Fake> targets = targets(24);
        var layout = layout(targets);
        var state = StateBank.capture("A", Recall.capture(targets));
        var bank = StateBank.empty("Bank", layout).withStates(List.of(state));
        targets.forEach(target -> target.value = 1);
        Map<String, Double> before = Recall.capture(targets);
        targets.get(10).failOnce = true;
        fails(() -> Recall.apply(bank, state, layout, targets, false, (label, action) -> action.run()));
        check(Recall.capture(targets).equals(before), "setter failure restores even the failing parameter");
        targets.get(10).ignoreOnce = true;
        fails(() -> Recall.apply(bank, state, layout, targets, false, (label, action) -> action.run()));
        check(Recall.capture(targets).equals(before), "readback detects silently ignored writes and rolls back");
        targets.get(10).alwaysFail = true;
        try {
            Recall.apply(bank, state, layout, targets, false, (label, action) -> action.run());
            throw new AssertionError("expected rollback failure");
        } catch (IllegalStateException error) {
            check(error.getMessage().contains("rollback failed"), "rollback failure is explicit");
            check(error.getSuppressed().length > 0, "failure evidence retained");
        }
    }

    private static void persistenceAndMalformedInput() throws Exception {
        var targets = targets(24);
        var state = StateBank.capture("Cutoff ü / 短", Recall.capture(targets));
        var bank = StateBank.empty("Polymer & Reverb", layout(targets)).withStates(List.of(state));
        String json = BankStore.encode(bank);
        check(BankStore.decode(json).equals(bank), "typed JSON roundtrip");
        Path folder = Files.createTempDirectory("state-bank-test-");
        try {
            new BankStore(folder).save(bank);
            check(new BankStore(folder).load(bank.id()).equals(bank), "bank persists across store instances");
            String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java" + suffix).toString(),
                    "-cp", System.getProperty("java.class.path"), StateTests.class.getName(),
                    "read-bank", folder.toString(), bank.id()).redirectErrorStream(true).start();
            if (!child.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                child.destroyForcibly(); throw new AssertionError("Persistence child timed out");
            }
            String childOutput = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            check(child.exitValue() == 0 && childOutput.contains("CROSS_PROCESS_BANK_OK"), "bank survives Java process restart: " + childOutput);
            check(new BankStore(folder).ids().equals(List.of(bank.id())), "library index");
            var updated = bank.withStates(List.of(new StateBank.State(state.id(), "Renamed", state.values())));
            new BankStore(folder).save(updated);
            check(new BankStore(folder).load(bank.id()).equals(updated), "atomic replacement");
            try (var files = Files.list(folder)) { check(files.count() == 1, "no abandoned temporary files"); }
            Path blocked = folder.resolve("not-a-folder"); Files.writeString(blocked, "blocked");
            fails(() -> new BankStore(blocked).save(bank));
            check(new BankStore(folder).load(bank.id()).equals(updated), "failed save preserves existing bank");
            fails(() -> new BankStore(folder).load("../../escape"));
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        fails(() -> BankStore.decode(json.replace("\"formatVersion\": " + StateBank.FORMAT, "\"formatVersion\": 999")));
        fails(() -> BankStore.decode(json.replace("\"formatVersion\": " + StateBank.FORMAT, "\"formatVersion\": 1.5")));
        fails(() -> BankStore.decode("{\"formatVersion\":1,\"formatVersion\":1}"));
        fails(() -> BankStore.decode(json + "{}"));
        fails(() -> BankStore.decode("null"));
        fails(() -> BankStore.decode("{broken"));
        fails(() -> BankStore.decode("{/*comment*/\"formatVersion\":1}"));
        fails(() -> BankStore.decode(json.replaceFirst("\"formatVersion\"", "formatVersion")));
        fails(() -> BankStore.decode(json.replaceFirst("\"value-does-not-exist\"", "\"missing\"").substring(0, json.length() / 2)));
        var badValues = new LinkedHashMap<>(state.values()); badValues.remove(badValues.keySet().iterator().next());
        fails(() -> bank.withStates(List.of(new StateBank.State(state.id(), "Incomplete", badValues))));
        var nan = new LinkedHashMap<>(state.values()); nan.put(nan.keySet().iterator().next(), Double.NaN);
        fails(() -> new StateBank.State(state.id(), "NaN", nan));
        fails(() -> bank.withStates(List.of(state, state)));
    }

    private static List<Fake> targets(int count) {
        List<Fake> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boolean continuous = i % 3 == 0;
            var p = new StateBank.Parameter("root/slot/" + i + "/cutoff", "Parameter " + i,
                    continuous ? "continuous" : "discrete", continuous ? 0 : 3, continuous ? -48 : 0,
                    continuous ? 120 : 2, continuous ? List.of() : List.of("Off", "One", "Two"));
            targets.add(new Fake(p, continuous ? 0.321234567 : 0.5));
        }
        return targets;
    }
    private static StateBank.Layout layout(List<Fake> targets) {
        return new StateBank.Layout(List.of(new StateBank.Node("root", "native:polymer"),
                new StateBank.Node("root/fx/0", "native:reverb")), targets.stream().map(Fake::descriptor).toList());
    }
    private static final class Fake implements Recall.Target {
        final StateBank.Parameter descriptor;
        double value; boolean automated; boolean writable = true;
        boolean failOnce; boolean ignoreOnce; boolean alwaysFail; int writes;
        double offsetOnce;
        Fake(StateBank.Parameter descriptor, double value) { this.descriptor = descriptor; this.value = value; }
        public StateBank.Parameter descriptor() { return descriptor; }
        public boolean writable() { return writable; }
        public boolean automated() { return automated; }
        public double read() { return value; }
        public void write(double next) {
            writes++;
            if (alwaysFail) throw new IllegalStateException("Persistent setter failure");
            if (ignoreOnce) { ignoreOnce = false; return; }
            value = next + offsetOnce; offsetOnce = 0;
            if (failOnce) { failOnce = false; throw new IllegalStateException("Setter changed then failed"); }
        }
    }
    private interface Work { void run() throws Exception; }
    private static void fails(Work work) {
        try { work.run(); throw new AssertionError("Expected refusal"); }
        catch (Exception expected) { checks++; }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
