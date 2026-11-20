package com.bitwig.mods.lockstates;

import bitwig.obf.HEL; // native automation encoder
import bitwig.obf.iav; // native automation content
import bitwig.obf.lSj; // native note content
import bitwig.obf.pBi; // native note clip event
import bitwig.obf.n1; // native note event
import bitwig.obf.Rke; // played note lane
import bitwig.obf.la1; // native automation clip event
import bitwig.obf.bpM; // content timeline with scheduled engine re-encoding
import bitwig.obf.xJo; // track
import bitwig.obf.yiy; // automation lane (visible or hidden)
import bitwig.obf.btv; // launcher automation slot
import bitwig.obf.YvI; // native timeline event
import bitwig.obf.bU; // native automation domain
import bml.pianoroll.PianoRoll;
import bml.notifications.Notifications;
import bml.notifications.Severity;
import com.bitwig.flt.document.core.master.device.xlG;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Builds stepped native automation when clip content is encoded. No document changes happen on the playback clock. */
final class NativeSequence {
    private static final Map<iav, Boolean> CONTENTS = new WeakHashMap<>();
    private static final Map<iav, String> ERRORS = new WeakHashMap<>();
    private static final AtomicBoolean POSTED = new AtomicBoolean();
    private static boolean initialized;
    private static final Map<Source, Prepared> PREPARED = new HashMap<>();
    private static final Map<xJo, List<xlG>> ROOTS = new HashMap<>();
    private static boolean clearPosted;
    private static String lastMessage = "";
    private static long lastMessageTime;
    private record Source(xlG owner, pBi clip) { }
    private record Prepared(String encoded, DeviceStates data, NativeDevice.Read read, Map<String, Recall.Target> targets, SequencePlan.Program program) { }

    private record Decoded(String encoded, DeviceStates data) { }
    private static final Map<xlG, Decoded> DECODED = new HashMap<>();

    private static void scheduleClear() {
        if (initialized && !clearPosted) {
            clearPosted = true;
            NativeDevice.later(() -> { PREPARED.clear(); ROOTS.clear(); DECODED.clear(); clearPosted = false; });
        }
    }

    /** One strict decode per owner and encoding batch; every managed curve of that owner shares it. */
    static DeviceStates decoded(xlG owner, String encoded) {
        var cached = DECODED.get(owner);
        if (cached != null && cached.encoded().equals(encoded)) return cached.data();
        var data = DeviceStates.decode(encoded);
        DECODED.put(owner, new Decoded(encoded, data));
        scheduleClear(); // same short lifetime as PREPARED
        return data;
    }

    private static Prepared prepared(xlG owner, pBi clip, String token, String encoded) {
        var key = new Source(owner, clip);
        var cached = PREPARED.get(key);
        if (cached != null && cached.encoded().equals(encoded)) return cached;
        var read = NativeDevice.read(owner);
        var targets = new HashMap<String, Recall.Target>(); read.targets().forEach(target -> targets.put(target.descriptor().key(), target));
        var data = decoded(owner, encoded);
        var result = new Prepared(encoded, data, read, targets, SequencePlan.compile(events(clip, token, Map.of()), data.snapshots(), read.layout()));
        if (data.notePlayback()) for (var target : read.targets()) if (result.program().changes().containsKey(target.descriptor().key())) {
            var lane = clip.lDU().mvz().UNj(NativeDevice.automation(target));
            checkLane(lane, token); // one conflicting lane suspends the complete generated state, not just one parameter
            var curve = NativeAutomation.find(lane, clip);
            if (curve == null) throw new IllegalArgumentException("State parameters changed or a generated lane was removed. Assign the state to its notes again.");
            if (!CurveSpec.decode(SequenceStorage.read(curve.SVQ())).parameter().compatible(target.descriptor())) throw new IllegalArgumentException("A generated lane's parameter changed. Remove its automation before assigning note states again.");
        }
        PREPARED.put(key, result);
        scheduleClear(); // short-lived batch cache never keeps closed projects alive
        return result;
    }

    static synchronized void remember(iav content) { CONTENTS.put(content, Boolean.TRUE); }
    static void initialize() {
        if (initialized) return;
        initialized = true;
        PianoRoll.onChange(NativeSequence::invalidate);
    }
    static void notesEncoded(lSj content) { if (initialized) invalidate(); }
    static void invalidate() {
        PREPARED.clear();
        ROOTS.clear();
        DECODED.clear();
        if (!initialized || !POSTED.compareAndSet(false, true)) return;
        NativeDevice.later(() -> {
            POSTED.set(false);
            List<iav> contents;
            synchronized (NativeSequence.class) { contents = List.copyOf(CONTENTS.keySet()); }
            for (var content : contents) if (content.UNj(com.bitwig.flt.document.core.master.g2L.class) != null) ((bpM) content).qm(); // native end-of-event engine rebuild
        });
    }

    static List<SequencePlan.Event> events(pBi clip, String owner, Map<n1, String> replacements) {
        var result = new ArrayList<SequencePlan.Event>();
        for (Object item : clip.mtS().FFu().wGc()) if (item instanceof Rke lane) for (Object played : lane.cfM()) {
            if (!(played instanceof n1 note)) continue;
            var bindings = NoteAssignments.decode(replacements.getOrDefault(note, SequenceStorage.read(note)));
            String state = bindings.states().get(owner);
            if (state == null || Boolean.TRUE.equals(note.VpW("is_muted").getValue())) continue;
            NativeNoteConditions.validate(note);
            result.add(new SequencePlan.Event(note.x3t(), state));
        }
        return List.copyOf(result);
    }

    static void encode(List output, iav content) {
        String stored = SequenceStorage.read(content);
        if (stored.isEmpty()) { HEL.cfM(output, content); return; } // original native encoder for every unowned timeline
        remember(content);
        boolean preserve = true;
        try {
            var spec = CurveSpec.decode(stored);
            preserve = !NativeEnvelope.pristine(content, spec);
            if (preserve) throw new IllegalArgumentException("A generated Lock States automation lane was edited. Its manual points are preserved; note playback is suspended.");
            bU range = content.LEd() == null ? null : content.LEd().MIO();
            var wanted = NativeAutomation.render(content, source -> {
                    xlG owner = findOwner(source.lDU(), spec.owner());
                    String data = owner == null ? "" : NativeStorage.read(owner);
                    if (!data.isEmpty() && decoded(owner, data).notePlayback()) {
                        var prepared = prepared(owner, source, spec.owner(), data);
                        var target = prepared.targets().get(spec.parameter().key());
                        if (target != null && spec.parameter().compatible(target.descriptor())) {
                            var actual = target; // removed/replaced targets never migrate to a neighbor
                            return prepared.program().points(target.descriptor().key(), spec.baseline()).stream()
                                    .map(point -> new SequencePlan.Point(point.time(), NativeDevice.raw(actual, point.value()))).toList();
                        }
                    }
                return List.of();
            });
            if (range != null && !wanted.isEmpty()) NativeEnvelope.append(output, range, wanted);
            synchronized (NativeSequence.class) { ERRORS.remove(content); }
        } catch (RuntimeException | LinkageError failure) {
            // Never overwrite genuine points, and never emit a half-built generated program.
            output.clear();
            if (preserve) HEL.cfM(output, content);
            report(content, failure);
        }
    }

    private static void report(iav content, Throwable failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        synchronized (NativeSequence.class) { if (message.equals(ERRORS.put(content, message))) return; }
        long now = System.nanoTime();
        if (message.equals(lastMessage) && now - lastMessageTime < 2_000_000_000L) return;
        lastMessage = message; lastMessageTime = now;
        LockStates.MOD.log("Note playback: " + message, failure);
        if (initialized) {
            NativeDevice.later(() -> Notifications.post(Severity.WARNING, "Lock States", message));
        }
    }

    static xlG findOwner(xJo track, String token) {
        if (track == null) return null;
        xlG found = null;
        scheduleClear();
        for (var device : ROOTS.computeIfAbsent(track, NativeDevice::roots)) {
            String identity = NativeStorage.identity(device);
            if (!identity.startsWith(token + "/")) continue;
            if (found != null) throw new IllegalArgumentException("Copied parent devices share a note state identity. Bind notes to the desired parent again.");
            found = device;
        }
        return found;
    }

    record Destination(Recall.Target target, com.bitwig.flt.document.core.master.eD address, CurveSpec spec) { }

    static boolean managedOnly(bitwig.obf.bDH atom) {
        Object ancestor = atom.IwV(xlG.class); // parameter's owning device
        if (!(ancestor instanceof xlG device)) return false;
        var owner = NativeDevice.owner(device); var track = NativeDevice.track(owner);
        if (track == null) return false;
        String identity = NativeStorage.identity(owner);
        if (identity.isEmpty()) return false;
        var lane = track.mvz().UNj(com.bitwig.flt.document.core.master.eD.cfM(atom));
        if (lane == null) return false;
        try { checkLane(lane, identity.substring(0, identity.indexOf('/'))); return true; }
        catch (IllegalArgumentException blocked) { return false; }
    }

    static void clear(List<bml.pianoroll.Note> notes) {
        if (notes.isEmpty()) return;
        var before = new IdentityHashMap<n1, String>();
        for (var note : notes) if (note.bitwigNote() instanceof n1 event) before.put(event, SequenceStorage.read(event));
        if (before.values().stream().allMatch(String::isEmpty)) return;
        NativeDevice.transaction(before.keySet().iterator().next(), "Clear Note Lock States", () -> {
            try { before.keySet().forEach(note -> SequenceStorage.write(note, "")); }
            catch (RuntimeException | LinkageError failure) { before.forEach((note, data) -> SequenceStorage.write(note, data)); throw failure; }
        });
        invalidate();
    }

    static void bind(List<bml.pianoroll.Note> notes, xlG owner, String state, boolean remove) {
        if (!remove && NativeDevice.automationWriting(owner)) throw new IllegalArgumentException("Stop automation recording before assigning note states");
        if (notes.isEmpty()) throw new IllegalArgumentException("Select notes in one clip");
        Object raw = notes.getFirst().clip() == null ? null : notes.getFirst().clip().bitwigClip();
        if (!(raw instanceof pBi clip) || clip.lDU() == null || NativeDevice.track(owner) != clip.lDU()) throw new IllegalArgumentException("Choose a state owner on the selected clip's track");
        for (var note : notes) if (note.clip() == null || note.clip().bitwigClip() != clip || !(note.bitwigNote() instanceof n1)) throw new IllegalArgumentException("Select notes in exactly one clip");
        String originalData = NativeStorage.read(owner);
        if (originalData.isEmpty()) throw new IllegalArgumentException("Capture a state on this parent device first");
        DeviceStates data = DeviceStates.decode(originalData);
        var read = NativeDevice.read(owner);
        var bank = read.upgrade(data.snapshots());
        if (!remove && bank.states().stream().noneMatch(item -> item.id().equals(state))) throw new IllegalArgumentException("The selected state no longer exists");
        var roots = NativeDevice.roots(clip.lDU());
        var sources = roots.stream().map(root -> new DeviceIdentity.Source(NativeStorage.identity(root), root.kFU().toString())).toList();
        var identities = DeviceIdentity.plan(sources);
        int ownerIndex = roots.indexOf(owner);
        if (ownerIndex < 0) throw new IllegalArgumentException("The state owner is not a top-level device on this track");
        String token = identities.get(ownerIndex).token();
        var changes = new IdentityHashMap<n1, String>(); var beforeNotes = new IdentityHashMap<n1, String>();
        for (var note : notes) {
            n1 event = (n1) note.bitwigNote(); String before = SequenceStorage.read(event); beforeNotes.put(event, before);
            changes.put(event, NoteAssignments.decode(before).with(token, remove ? null : state).encode());
        }
        var program = SequencePlan.events(events(clip, token, changes), bank); // reject simultaneous conflicting states before writes
        var compiled = SequencePlan.compile(program, bank, read.layout());
        if (!remove && SequencePlan.compile(List.of(new SequencePlan.Event(0, state)), bank, read.layout()).changes().isEmpty()) throw new IllegalArgumentException("This state has no compatible parameters on the current device");
        var destinations = new ArrayList<Destination>();
        if (!remove) for (var target : read.targets()) {
            boolean used = compiled.changes().containsKey(target.descriptor().key());
            if (!used) continue;
            if (!target.writable()) throw new IllegalArgumentException("Parameter is not writable: " + target.descriptor().name());
            var address = NativeDevice.automation(target);
            if (address.MIO() == null) throw new IllegalArgumentException("Parameter cannot be sequenced: " + target.descriptor().name());
            var existing = clip.lDU().mvz().UNj(address); // read-only lookup, never create or replace user automation during preflight
            checkLane(existing, token);
            var existingCurve = NativeAutomation.find(existing, clip);
            double baseline = target.read();
            CurveSpec spec = existingCurve == null ? new CurveSpec(1, token, target.descriptor(), baseline, NativeDevice.raw(target, baseline)) : CurveSpec.decode(SequenceStorage.read(existingCurve.SVQ()));
            if (spec.encode().length() > 65536) throw new IllegalArgumentException("This parameter's note state metadata exceeds the native storage limit");
            var rawPoints = compiled.points(target.descriptor().key(), spec.baseline()).stream()
                    .map(point -> new SequencePlan.Point(point.time(), NativeDevice.raw(target, point.value()))).toList();
            NativeEnvelope.preflight(address.MIO(), rawPoints, target.descriptor().name()); // prove construction AND native encoding before project edits
            destinations.add(new Destination(target, address, spec));
        }
        var identityChanges = new ArrayList<>(read.identities());
        for (int i = 0; i < roots.size(); i++) if (!sources.get(i).stored().equals(identities.get(i).stored())) {
            identityChanges.add(new NativeDevice.IdentityEdit(roots.get(i), sources.get(i).stored(), identities.get(i).stored()));
        }
        var commitRead = new NativeDevice.Read(read.layout(), read.targets(), read.legacyLayout(), read.legacyKeys(), List.copyOf(identityChanges));
        var created = new ArrayList<la1>();
        var createdLanes = new ArrayList<bitwig.obf.lkr>(); // persistent lanes need rollback as well as clips
        NativeDevice.transaction(owner, remove ? "Remove Note Lock States" : "Assign Note Lock State", () -> NativeDevice.withIdentities(commitRead, () -> {
            try {
                for (var destination : destinations) {
                    var lanes = clip.lDU().mvz();
                    var existingLane = lanes.Hnt((com.bitwig.flt.document.core.iface.z2y) destination.address()); // persistent lanes only; ignore deferred placeholders
                    var lane = NativeAutomation.materialize(lanes, destination.address());
                    if (existingLane == null) createdLanes.add(lane);
                    NativeAutomation.ensure(lane, clip, destination.spec(), created);
                }
                changes.forEach(SequenceStorage::write);
                NativeStorage.write(owner, data.with(bank, data.selected()).playback(remove ? data.notePlayback() : true).encode());
                for (var destination : destinations) NativeDevice.restoreAutomation(destination.target());
            } catch (RuntimeException | LinkageError failure) {
                beforeNotes.forEach((note, before) -> { try { SequenceStorage.write(note, before); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); } });
                try { NativeStorage.write(owner, originalData); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                for (int i = created.size() - 1; i >= 0; i--) try { created.get(i).cfM((bitwig.obf.Bz) null); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                for (int i = createdLanes.size() - 1; i >= 0; i--) try { NativeAutomation.remove(createdLanes.get(i)); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }));
        invalidate();
    }

    static void checkLane(yiy lane, String token) {
        if (lane == null) return;
        if (!lane.LaE().TMF().isEmpty() && !NativeAutomation.managed(lane, token)) throw new IllegalArgumentException("Existing arrangement automation blocks note states. Its points were left unchanged.");
        for (Object item : lane.LaE().cfM()) if (item instanceof la1 curve) checkCurve(curve, token);
        for (Object item : lane.ODA().UZr()) if (item instanceof btv slot && slot.UNj() instanceof la1 curve) checkCurve(curve, token);
    }
    private static void checkCurve(la1 curve, String token) {
        String data = SequenceStorage.read(curve.SVQ());
        if (data.isEmpty() || !CurveSpec.decode(data).owner().equals(token) || !NativeEnvelope.pristine(curve.SVQ(), CurveSpec.decode(data))) throw new IllegalArgumentException("Existing clip automation blocks note states. Its points were left unchanged.");
    }
}
