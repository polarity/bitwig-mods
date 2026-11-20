package com.bitwig.mods.lockstates;

import com.bitwig.flt.document.core.master.NcW; // native document schema definitions
import bitwig.obf.deO; // isolated document schema registry
import com.bitwig.flt.document.core.master.device.AxS; // native device document
import com.bitwig.ramona.serial.iYN; // native serialization options
import com.bitwig.ramona.core.bbQ; // native document cloning
import com.bitwig.ramona.core.oDc;
import bitwig.obf.dfg; // native persisted string descriptor and Undo delta
import bitwig.obf.foz; // native property observer
import bitwig.obf.dfb; // native ownership field descriptor
import bitwig.obf.n1; // native note
import bitwig.obf.iav; // native automation content
import bitwig.obf.bd; // signed integer automation domain
import bitwig.obf.aX; // indexed automation domain
import bitwig.obf.aQ; // ordered indexed choice
import bitwig.obf.aL; // continuous automation domain
import bitwig.obf.aI; // Boolean automation domain
import bitwig.obf.bU; // native automation domain
import bitwig.obf.fW; // continuous scale mode
import bitwig.obf.fX; // continuous formatting mode
import bitwig.obf.ji2; // occurrence modes
import bitwig.obf.xJo; // native track
import bitwig.obf.wsY; // MIDI parameter kind used by the native track fixture
import bitwig.obf.g1d; // native note clip
import bitwig.obf.la1; // native automation clip
import bitwig.obf.YvI; // native timeline event
import bitwig.obf.Xa; // application service interface (only editor-mode flag is stubbed)
import bitwig.obf.iJW; // application service holder
import bitwig.obf.GVR; // native arrangement automation and end-event maintenance
import com.bitwig.flt.document.core.master.eD;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Runs against the packaged mod and the loader's transformed native document classes, without a DAW or engine. */
public final class NativeStorageTests {
    private static int checks;
    static final class EmptyDevice extends AxS {
        EmptyDevice() { Hnt(java.util.UUID.randomUUID()); } // initialize the native instance UUID; no instrument/engine
    }

    public static void main(String[] args) throws Exception {
        try { run(args); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0); // native track fixtures initialize event threads; do not leave test JVMs running
    }

    private static void run(String[] args) throws Exception {
        var registry = new deO();
        NcW.cfM(registry); // register real core document types without loading Nitro resources or application services
        if (args.length != 2) throw new IllegalArgumentException("Expected write|read and an isolated fixture directory");
        Path folder = Path.of(args[1]);
        if (args[0].equals("read")) {
            var schema = new EmptyDevice().nh_();
            oDc reopened = (oDc) schema.cfM(Files.readAllBytes(folder.resolve("device.bin")));
            check(DeviceStates.decode(NativeStorage.read(reopened)).equals(payload()), "states and selection survive a fresh Java process");
            check(NativeStorage.identity(reopened).startsWith("44444444-4444-4444-8444-444444444444/"), "device identity survives a fresh Java process");
            oDc another = (oDc) schema.cfM(Files.readAllBytes(folder.resolve("other.bin")));
            check(NativeStorage.read(another).isEmpty(), "second instance stays empty across processes");
            oDc malformed = (oDc) schema.cfM(Files.readAllBytes(folder.resolve("malformed.bin")));
            check(NativeStorage.read(malformed).equals("{broken"), "unknown/malformed data survives native roundtrip unchanged");
            boolean refused = false;
            try { DeviceStates.decode(NativeStorage.read(malformed)); }
            catch (IllegalArgumentException expected) { refused = true; }
            check(refused && NativeStorage.read(malformed).equals("{broken"), "malformed state lists are refused without clearing data");
            n1 reopenedNote = (n1) n1.cfM(0, 1, 0.8).nh_().cfM(Files.readAllBytes(folder.resolve("note.bin")));
            check(NoteAssignments.decode(SequenceStorage.read(reopenedNote)).states().get("44444444-4444-4444-8444-444444444444").equals(payload().selected()), "individual note assignments survive a new Java process");
            iav reopenedCurve = (iav) iav.cfM(bd.cfM(0, 127, 0).sut()).nh_().cfM(Files.readAllBytes(folder.resolve("sequence.bin")));
            check(CurveSpec.decode(SequenceStorage.read(reopenedCurve)).parameter().key().equals("cutoff"), "native sequence metadata survives a new Java process");
            System.out.println("LOCK_STATES_NATIVE_STORAGE_RESTART_OK checks=" + checks);
            return;
        }
        if (!args[0].equals("write")) throw new IllegalArgumentException("Unknown test operation");
        Files.createDirectory(folder);
        var first = new EmptyDevice(); first.iy_();
        var second = new EmptyDevice(); second.iy_();
        check(NativeStorage.read(first).isEmpty() && NativeStorage.read(second).isEmpty(), "fresh instances have no states");
        var data = payload();
        String encoded = data.encode();
        var descriptor = (dfg) first.nh_().Hnt(NativeStorage.KEY); // native schema field lookup
        check(descriptor != null && descriptor.E3() == NativeStorage.ID, "schema owns the declared field ID");
        check(descriptor.UNj("not_engine") && descriptor.UNj("dont_send_to_slave"), "editor data is excluded from engine/slave traffic");
        int[] events = {0};
        foz listener = ignored -> events[0]++;
        descriptor.cfM(first, listener); // native observer registration
        NativeStorage.write(first, encoded);
        check(NativeStorage.read(first).equals(encoded), "native property setter/getter");
        check(NativeStorage.read(second).isEmpty(), "state lists do not leak between instances");
        check(events[0] == 1, "native property observer fires on edits");
        NativeStorage.write(first, encoded);
        check(events[0] == 1, "same-value writes produce no change event");
        descriptor.MRY(first, listener); // native observer removal
        String identity = "44444444-4444-4444-8444-444444444444/" + first.kFU();
        NativeStorage.writeIdentity(first, identity);
        check(NativeStorage.identity(first).equals(identity), "identity uses its own serialized property");
        check(NativeStorage.read(first).equals(encoded) && events[0] == 1, "identity edits do not replace state data or notify state observers");
        var identityDescriptor = (dfg) first.nh_().Hnt(NativeStorage.IDENTITY_KEY);
        var identityChange = identityDescriptor.cfM(NativeStorage.IDENTITY_KEY, "", identity);
        identityChange.cfM(identityDescriptor.cfM(first), null);
        check(NativeStorage.identity(first).isEmpty() && NativeStorage.read(first).equals(encoded), "native identity Undo preserves the state list");
        identityChange.MRY(identityDescriptor.cfM(first), null);
        check(NativeStorage.identity(first).equals(identity), "native identity Redo restores the token");
        var change = descriptor.cfM(NativeStorage.KEY, "", encoded); // native string Undo delta
        change.cfM(descriptor.cfM(first), null); // undo to the previous property value
        check(NativeStorage.read(first).isEmpty(), "native Undo delta restores previous states");
        change.MRY(descriptor.cfM(first), null); // redo the property value
        check(NativeStorage.read(first).equals(encoded), "native Redo delta restores states and selection");
        check(events[0] == 1, "removed observers stay removed");
        first.iHb("Native name"); // native user_device_name setter: unrelated string dispatch must still work
        check(first.Ai().equals("Native name"), "existing native string setters remain functional");
        byte[] bytes = first.FzB(iYN.Hnt);
        oDc reopened = (oDc) first.nh_().cfM(bytes);
        check(NativeStorage.read(reopened).equals(encoded), "native document serialization retains the full payload");
        check(NativeStorage.identity(reopened).equals(identity), "native serialization retains the instance identity");
        oDc duplicate = bbQ.cfM().MRY(first);
        check(duplicate != first && NativeStorage.read(duplicate).equals(encoded), "native cloning copies the list into a distinct instance");
        check(NativeStorage.identity(duplicate).equals(identity), "whole-device cloning retains relative snapshot identity");
        NativeStorage.writeIdentity(duplicate, "55555555-5555-4555-8555-555555555555/" + first.kFU());
        check(NativeStorage.identity(first).equals(identity), "identity edits on a clone leave its source unchanged");
        NativeStorage.write(duplicate, "");
        check(NativeStorage.read(first).equals(encoded), "editing a duplicate leaves its source unchanged");
        NativeStorage.write(second, encoded);
        check(NativeStorage.read(second).equals(encoded), "second instance can have its own list");
        NativeStorage.write(second, "");
        check(NativeStorage.read(first).equals(encoded), "clearing another instance leaves the first intact");
        Files.write(folder.resolve("device.bin"), bytes);
        Files.write(folder.resolve("other.bin"), second.FzB(iYN.Hnt));
        NativeStorage.write(second, "{broken");
        Files.write(folder.resolve("malformed.bin"), second.FzB(iYN.Hnt));
        var parent = new EmptyDevice(); var middle = new EmptyDevice(); var leaf = new EmptyDevice();
        // Build bare native ownership links; these exercise selection routing, not an initialized instrument/FX graph.
        middle.cfM((dfb) parent.rGR().UNj(), parent);
        leaf.cfM((dfb) middle.rGR().UNj(), middle);
        check(NativeDevice.owner(leaf) == parent, "deeply nested selection resolves to the outermost device");
        check(NativeDevice.owner(middle) == parent && NativeDevice.owner(parent) == parent, "one parent owns the shared list at every nested depth");
        var adjacent = new EmptyDevice();
        check(NativeDevice.owner(adjacent) == adjacent, "adjacent track devices keep independent owners");
        leaf.cfM((dfb) null, (oDc) null);
        check(NativeDevice.owner(leaf) == leaf, "moving a device out of its parent restores standalone ownership");
        var rollbackRead = new NativeDevice.Read(data.snapshots().layout(), List.of(), data.snapshots().layout(), Map.of(),
                List.of(new NativeDevice.IdentityEdit(first, identity, "66666666-6666-4666-8666-666666666666/" + first.kFU()),
                        new NativeDevice.IdentityEdit(parent, "", "77777777-7777-4777-8777-777777777777/" + parent.kFU())));
        boolean failed = false;
        try { NativeDevice.withIdentities(rollbackRead, () -> { throw new IllegalStateException("commit failed"); }); }
        catch (IllegalStateException expected) { failed = true; }
        check(failed && NativeStorage.identity(first).equals(identity) && NativeStorage.identity(parent).isEmpty(), "failed state commits roll every identity assignment back");
        check(NativeStorage.read(first).equals(encoded), "identity rollback leaves the existing snapshot list intact");
        nativeSequence(folder);
        noteConditions();
        automationInsertion();
        System.out.println("LOCK_STATES_NATIVE_STORAGE_OK checks=" + checks);
    }

    private static void automationInsertion() throws Exception {
        // Native geometry setters consult one application editing-mode flag even
        // without a project. Supply only that flag; all unexpected service calls fail.
        // This tests real document mutation, not an initialized UI or audio engine.
        var observed = new java.util.concurrent.atomic.AtomicReference<java.lang.reflect.Method>();
        Xa recorder = (Xa) java.lang.reflect.Proxy.newProxyInstance(Xa.class.getClassLoader(), new Class<?>[]{Xa.class},
                (object, method, arguments) -> { observed.set(method); return false; });
        recorder.BAa();
        var service = (Xa) java.lang.reflect.Proxy.newProxyInstance(Xa.class.getClassLoader(), new Class<?>[]{Xa.class},
                (object, method, arguments) -> {
                    if (method.equals(observed.get())) return false;
                    throw new AssertionError("Unexpected application service in document canary: " + method);
                });
        var field = java.util.Arrays.stream(iJW.class.getDeclaredFields()).filter(item -> item.getType() == Xa.class).findFirst().orElseThrow();
        field.setAccessible(true); var original = field.get(null); field.set(null, service);
        try {
            var track = xJo.Mb2();
            var address = eD.cfM(track.iyL().cfM(0, wsY.cfM, 74));
            var lanes = track.mvz();
            var placeholder = lanes.cfM(address, true);
            check(placeholder.LaE().jG(), "deferred automation lanes are explicitly marked pending");
            var source = g1d.cfM(2.0, 4.0);
            track.oLU().iAj().cfM((YvI) source, 0);
            var node = new StateBank.Parameter("fixture", "Fixture", "continuous", 0, 0, 1, List.of());
            var spec = new CurveSpec(1, "44444444-4444-4444-8444-444444444444", node, 0.5, ((aL) address.MIO()).MRY(0.5));
            var rejected = la1.cfM(2.0, 4.0, 0.0, iav.cfM(address));
            try { placeholder.LaE().cfM((YvI) rejected, 0); throw new AssertionError("Expected deferred lane refusal"); }
            catch (IllegalStateException expected) { check(expected.getMessage().contains("about to be created lane"), "old insertion path reproduces the live error"); }

            var lane = NativeAutomation.materialize(lanes, address);
            check(!lane.LaE().jG() && lanes.Hnt((com.bitwig.flt.document.core.iface.z2y) address) == lane, "materialization creates a registered persistent lane");
            check(lane.LaE().TMF().isEmpty(), "materialization creates no competing global arrangement automation");
            check(NativeAutomation.materialize(lanes, address) == lane && lanes.cfM().size() == 1, "existing persistent lanes are reused");
            var created = new java.util.ArrayList<la1>();
            var curve = NativeAutomation.insert(lane, source, spec, created);
            check(created.equals(List.of(curve)) && lane.LaE().cfM().contains(curve), "production insertion attaches the automation clip and tracks rollback");
            check(curve.bB() == source, "arrangement automation resolves its actual source note clip");
            check(curve.x3t() == source.x3t() && curve.QWG() == source.QWG() && curve.dC_() == source.dC_(), "arrangement placement and content offset follow the source");
            check(NativeEnvelope.pristine(curve.SVQ(), spec), "inserted automation retains its recognizable baseline");
            var note = source.MRY(0, 60, 1.0, 1.0, 0.8);
            SequenceStorage.write(note, NoteAssignments.empty().with(spec.owner(), payload().selected()).encode());
            check(NativeSequence.events(source, spec.owner(), Map.of()).equals(List.of(new SequencePlan.Event(1, payload().selected()))), "assigned native notes resolve to the stored state and content beat");

            runNativeRepair(lane.LaE());
            check(!lane.LaE().TMF().isEmpty(), "native end-event maintenance adds the real surrounding repair points");
            NativeSequence.checkLane(lane, spec.owner());
            check(NativeAutomation.managed(lane, spec.owner()), "native repair points on owned clips do not block subsequent assignments");
            check(NativeAutomation.managed(lane, null), "managed lanes are recognized without an owner token");
            var second = source.MRY(0, 64, 2.0, 1.0, 0.8);
            var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "fixture")), List.of(node));
            var a = StateBank.capture("First", Map.of(node.key(), 0.2), layout);
            var b = StateBank.capture("Second", Map.of(node.key(), 0.8), layout);
            var bank = StateBank.empty("Two notes", layout).withStates(List.of(a, b));
            SequenceStorage.write(note, NoteAssignments.empty().with(spec.owner(), a.id()).encode());
            var beforeSecond = NativeAutomation.render(curve.SVQ(), clip -> rawFixture(clip, spec, bank, layout, address));
            SequenceStorage.write(second, NoteAssignments.empty().with(spec.owner(), b.id()).encode());
            check(NativeAutomation.ensure(lane, source, spec, created) == curve && created.size() == 1 && lanes.cfM().size() == 1, "assigning another note reuses the same clip and lane after native repair");
            runNativeRepair(lane.LaE());
            NativeSequence.checkLane(lane, spec.owner());
            var afterSecond = NativeAutomation.render(curve.SVQ(), clip -> rawFixture(clip, spec, bank, layout, address));
            check(!beforeSecond.equals(afterSecond) && afterSecond.size() == beforeSecond.size() + 1, "the actual note-clip resolver updates playback data for the second state");
            var envelope = NativeEnvelope.timeline(address.MIO(), afterSecond);
            check(Math.abs(envelope.cfM(1.5, true, address.MIO()) - 0.2) < 1e-6 && Math.abs(envelope.cfM(2.5, true, address.MIO()) - 0.8) < 1e-6, "native playback holds state A then state B at the second assigned note");
            var encoded = new java.util.ArrayList<>(); NativeEnvelope.append(encoded, address.MIO(), afterSecond);
            check(!encoded.isEmpty(), "two assigned note states generate actual native engine events");

            var global = lane.LaE().tSS();
            var manual = address.MIO().MRY(0.5, 0.77, bitwig.obf.jRs.MRY, 0);
            global.cfM((YvI) manual, 1);
            check(!NativeAutomation.managed(lane, spec.owner()) && !NativeAutomation.managed(lane, null), "genuine manual global automation is not managed");
            try { NativeSequence.checkLane(lane, spec.owner()); throw new AssertionError("Expected user automation refusal"); }
            catch (IllegalArgumentException expected) { check(global.cfM().contains(manual), "user automation conflict preserves its points"); }
            global.cfM((YvI) manual, global.cfM().indexOf(manual), true);
            check(NativeAutomation.managed(lane, spec.owner()), "removing a manual conflict restores managed-lane recognition");
            var boundary = (bitwig.obf.vOB) global.hUR(0);
            double originalBoundary = boundary.cfM(); boundary.cfM(originalBoundary + 0.12);
            check(!NativeAutomation.managed(lane, spec.owner()) && !NativeAutomation.managed(lane, null), "editing an existing native boundary makes the lane manual");
            try { NativeSequence.checkLane(lane, spec.owner()); throw new AssertionError("Expected edited-boundary refusal"); }
            catch (IllegalArgumentException expected) { check(boundary.cfM() == originalBoundary + 0.12, "edited native boundary values are never overwritten"); }
            boundary.cfM(originalBoundary);

            byte[] bytes = track.FzB(iYN.Hnt);
            var reopened = (xJo) track.nh_().cfM(bytes);
            var restoredAddress = eD.cfM(reopened.iyL().MRY(0, wsY.cfM, 74));
            var restoredLane = reopened.mvz().Hnt((com.bitwig.flt.document.core.iface.z2y) restoredAddress);
            check(restoredLane != null && restoredLane.LaE().cfM().size() == 1, "persistent automation lanes and arrangement clips survive native track serialization");
            var restoredCurve = (la1) restoredLane.LaE().cfM().getFirst();
            check(CurveSpec.decode(SequenceStorage.read(restoredCurve.SVQ())).equals(spec) && restoredCurve.bB() != null, "reopened arrangement metadata and source relationship remain intact");
            check(SequencePlan.events(NativeSequence.events(restoredCurve.bB(), spec.owner(), Map.of()), bank).equals(List.of(new SequencePlan.Event(1, a.id()), new SequencePlan.Event(2, b.id()))), "reopened native track retains both note assignments and beat positions regardless of native pitch-lane order");
            check(NativeAutomation.managed(restoredLane, spec.owner()) && NativeAutomation.managed(restoredLane, null), "native repair recognition survives track serialization");

            for (int i = created.size() - 1; i >= 0; i--) created.get(i).cfM((bitwig.obf.Bz) null);
            NativeAutomation.remove(lane);
            check(lanes.Hnt((com.bitwig.flt.document.core.iface.z2y) address) == null && lanes.cfM().isEmpty(), "failed assignment cleanup can remove all created clips and persistent lanes");
        } finally { field.set(null, original); }
    }

    private static List<SequencePlan.Point> rawFixture(bitwig.obf.pBi clip, CurveSpec spec, StateBank bank, StateBank.Layout layout, eD address) {
        var program = SequencePlan.compile(NativeSequence.events(clip, spec.owner(), Map.of()), bank, layout);
        var domain = (aL) address.MIO();
        return program.points(spec.parameter().key(), spec.baseline()).stream().map(point -> new SequencePlan.Point(point.time(), domain.MRY(point.value()))).toList();
    }

    // Compile-check the private native lifecycle reference. The runner obtains its
    // finished name from modkit's access metadata, then reflection invokes it in
    // this isolated test JVM; no obfuscated method-name string is embedded here.
    private static void repairReference(GVR lane) { lane.Ai(); }

    private static void runNativeRepair(GVR lane) throws Exception {
        var method = GVR.class.getDeclaredMethod(System.getProperty("lockstates.canary.repair"));
        method.setAccessible(true); method.invoke(lane);
    }

    private static DeviceStates payload() {
        var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "device:test")),
                List.of(new StateBank.Parameter("cutoff", "Cutoff", "continuous", 0, 0, 1, List.of())));
        var first = new StateBank.State("11111111-1111-4111-8111-111111111111", "Saved ü / 短", Map.of("cutoff", 0.25), layout);
        var second = new StateBank.State("22222222-2222-4222-8222-222222222222", "Other", Map.of("cutoff", 0.75), layout);
        var bank = new StateBank(StateBank.FORMAT, "33333333-3333-4333-8333-333333333333", "Device States", layout, List.of(first, second));
        return new DeviceStates(DeviceStates.VERSION, bank, second.id(), true);
    }

    private static void nativeSequence(Path folder) throws Exception {
        String owner = "44444444-4444-4444-8444-444444444444";
        var note = n1.cfM(0, 1, 0.8);
        check(List.of("is_muted", "enable_occurrence", "enable_recurrence", "enable_chance").stream()
                .allMatch(key -> note.VpW(key).getValue() instanceof Boolean), "conditional-note guards resolve real native serialized fields");
        check(note.VpW("chance").getValue() instanceof Number, "native note chance is a numeric value");
        var assignment = NoteAssignments.empty().with(owner, payload().selected());
        SequenceStorage.write(note, assignment.encode());
        check(NoteAssignments.decode(SequenceStorage.read(note)).equals(assignment), "native note property stores per-owner assignments");
        var copied = (n1) bbQ.cfM().MRY(note);
        check(SequenceStorage.read(copied).equals(SequenceStorage.read(note)), "copying a native note carries its state binding");
        SequenceStorage.write(copied, "");
        check(!SequenceStorage.read(note).isEmpty(), "editing a note copy does not change its source binding");
        var property = (dfg) note.nh_().Hnt(SequenceStorage.NOTE_KEY);
        var delta = property.cfM(SequenceStorage.NOTE_KEY, "", assignment.encode());
        delta.cfM(property.cfM(note), null);
        check(SequenceStorage.read(note).isEmpty(), "native note Undo removes its binding");
        delta.MRY(property.cfM(note), null);
        check(SequenceStorage.read(note).equals(assignment.encode()), "native note Redo restores its binding");
        Files.write(folder.resolve("note.bin"), note.FzB(iYN.Hnt));
        var indexed = aX.IwV(0);
        indexed.MRY(aQ.cfM(0, "First")); indexed.MRY(aQ.cfM(-1, "Second")); indexed.MRY(aQ.cfM(-2, "Third"));
        List<bU> ranges = List.of(aL.cfM(0, 1, 0, fW.cfM, fW.cfM, fX.cfM), indexed, bd.cfM(Integer.MIN_VALUE, Integer.MAX_VALUE, 0), aI.Hnt(false), aI.Hnt(true));
        List<List<Double>> values = List.of(List.of(0.2, 0.8, 0.4), List.of(0.0, -1.0, -2.0), List.of(-2147483648.0, 0.0, 2147483647.0), List.of(0.0, 1.0, 0.0), List.of(1.0, 0.0, 1.0));
        for (int r = 0; r < ranges.size(); r++) {
            var range = ranges.get(r); var raw = values.get(r);
            var points = List.of(new SequencePlan.Point(0, raw.get(0)), new SequencePlan.Point(1, raw.get(1)), new SequencePlan.Point(2, raw.get(2)));
            var timeline = NativeEnvelope.timeline(range, points);
            check(timeline.ejd() == 3, "native timeline contains every bound position");
            for (int i = 0; i < 3; i++) {
                double actual = range.NC() ? range.cfM((bitwig.obf.dfo) timeline.hUR(i)) : timeline.hUR(i).Ucl();
                check(Math.abs(actual - raw.get(i)) < 1e-6, "native automation retains continuous, descending indexed and full integer values");
            }
            if (range.NC()) check(Math.abs(timeline.cfM(0.5, true, range) - raw.get(0)) < 1e-6, "native automation holds the preceding value between notes");
            var encoded = new java.util.ArrayList<>();
            NativeEnvelope.append(encoded, range, points);
            check(!encoded.isEmpty(), "native engine encoder emits held automation events");
            if (range instanceof aI) {
                check(timeline.cfM(0.5, true, range) == raw.get(0), "Boolean playback holds the first switch value");
                check(timeline.cfM(1.5, true, range) == raw.get(1), "Boolean playback switches at the assigned note");
                var descriptor = new StateBank.Parameter("switch", "Switch", "discrete", 2, 0, 1, List.of("Off", "On"));
                var marker = new CurveSpec(1, owner, descriptor, raw.get(0), raw.get(0));
                var content = iav.cfM(range.sut());
                NativeEnvelope.seed(content, range, marker); SequenceStorage.write(content, marker.encode());
                check(NativeEnvelope.pristine(content, marker), "Boolean baseline sentinels are recognizable");
                var clone = (iav) bbQ.cfM().MRY(content);
                check(NativeEnvelope.pristine(clone, marker), "cloned Boolean baselines remain recognizable");
                var reopened = (iav) content.nh_().cfM(content.FzB(iYN.Hnt));
                check(NativeEnvelope.pristine(reopened, marker) && CurveSpec.decode(SequenceStorage.read(reopened)).equals(marker), "serialized Boolean playback data retains its baseline and identity");
            }
        }
        var sequence = iav.cfM(bd.cfM(0, 127, 0).sut());
        var spec = new CurveSpec(1, owner, payload().snapshots().layout().parameters().getFirst(), 0.5, 64);
        NativeEnvelope.seed(sequence, bd.cfM(0, 127, 0), spec);
        SequenceStorage.write(sequence, spec.encode());
        check(sequence.TD2().ejd() == 1 && NativeEnvelope.pristine(sequence, spec), "baseline sentinel satisfies native nonempty automation invariants");
        check(CurveSpec.decode(SequenceStorage.read(sequence)).equals(spec), "native automation content retains its owner/key/baseline");
        var duplicate = (iav) bbQ.cfM().MRY(sequence);
        check(SequenceStorage.read(duplicate).equals(SequenceStorage.read(sequence)), "native automation content copies retain playback metadata");
        check(NativeEnvelope.pristine(duplicate, spec), "copied baseline remains recognizable");
        duplicate.TD2().cfM(bd.cfM(0, 127, 0).MRY(1, 0.2, null, 0), 1);
        check(!NativeEnvelope.pristine(duplicate, spec) && NativeEnvelope.pristine(sequence, spec), "manual point edits are detected without changing the source");
        Files.write(folder.resolve("sequence.bin"), sequence.FzB(iYN.Hnt));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void accepted(n1 note, String message) {
        NativeNoteConditions.validate(note);
        checks++;
    }

    private static void refused(n1 note, String field) {
        try { NativeNoteConditions.validate(note); }
        catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(field), "conditional refusal identifies the actual operator: " + field);
            return;
        }
        throw new AssertionError("Expected conditional refusal: " + field);
    }

    private static void noteConditions() throws Exception {
        var note = n1.cfM(0, 1, 0.8);
        check(note.ejd() && note.dnz(), "native default notes enable Occurrence and Recurrence switches");
        check(note.Z1C() == ji2.cfM && note.OzW() == 8, "native defaults mean Always and every recurrence");
        accepted(note, "a fresh ordinary note accepts state assignment");
        for (var mode : ji2.values()) {
            note.cfM(mode); // native occurrence mode setter
            note.UNj(true); // native occurrence enable setter
            if (mode == ji2.cfM) accepted(note, "explicit Always remains assignable");
            else refused(note, "Occurrence");
            note.UNj(false);
            accepted(note, "disabled occurrence ignores its retained mode");
        }
        note.cfM(ji2.cfM); note.UNj(true);
        for (int period = 2; period <= 8; period++) {
            int all = (1 << period) - 1;
            // Native recurrence serializes the period minus one in bits 0..2 and steps in bits 3..10.
            note.iHb((period - 1) | (all << 3)); note.hUR(true);
            accepted(note, "all enabled recurrence steps are unconditional at every supported period");
            for (int step : new int[]{0, period - 1}) {
                note.iHb((period - 1) | ((all & ~(1 << step)) << 3));
                refused(note, "Recurrence");
                note.hUR(false);
                accepted(note, "disabled recurrence ignores its retained pattern");
                note.hUR(true);
            }
        }
        note.iHb(8); note.hUR(true);
        note.IwV(true); note.TMF(1); // chance enable and probability setters
        accepted(note, "enabled Chance at 100 percent remains assignable");
        for (double chance : new double[]{0, 0.5, 0.99}) {
            note.TMF(chance); refused(note, "Chance");
            note.IwV(false); accepted(note, "disabled Chance ignores its retained probability");
            note.IwV(true);
        }
        note.TMF(1);
        var clone = (n1) bbQ.cfM().MRY(note);
        accepted(clone, "cloned ordinary note remains assignable");
        var reopened = (n1) note.nh_().cfM(note.FzB(iYN.Hnt));
        accepted(reopened, "serialized ordinary note remains assignable");
    }
}
