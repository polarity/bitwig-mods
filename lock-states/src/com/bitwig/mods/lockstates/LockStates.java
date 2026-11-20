package com.bitwig.mods.lockstates;

import bitwig.obf.aJc; // Device Inspector panel
import bitwig.obf.aCp; // project window that invoked a shortcut
import bitwig.obf.aYg; // Inspector selection controller
import bitwig.obf.kbG; // Inspector selection
import bitwig.obf.obZ; // selection of devices
import bitwig.obf.deN; // document field descriptor
import bitwig.obf.deS; // document object schema
import bitwig.obf.dfg; // serialized string property
import bitwig.obf.foz; // property listener
import bitwig.obf.dee; // native MIDI note schema
import bitwig.obf.Jzt; // native automation content schema
import bitwig.obf.HEL; // engine event encoder
import bitwig.obf.iav; // automation content
import bitwig.obf.lSj; // note content
import bitwig.obf.bro; // painter of a note grid
import bitwig.obf.Ibv; // a note, as the painter sees it
import com.bitwig.base.geom.DoubleRectangle;
import com.bitwig.graphics.SYo; // drawing surface
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import bml.inspector.BlockContext;
import bml.inspector.Blocks;
import bml.inspector.Context;
import bml.inspector.InspectorProvider;
import bml.inspector.Rows;
import bml.notifications.Notifications;
import bml.notifications.Severity;
import com.bitwig.flt.document.core.master.device.OCK; // common device schema
import com.bitwig.flt.document.core.master.device.xlG; // selected device
import com.bitwig.ramona.core.OLR;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

public final class LockStates implements InspectorProvider {
    static final Bml.Mod MOD = Bml.mod("lock-states");
    private static final Map<xlG, Session> SESSIONS = new WeakHashMap<>();
    private static final List<WeakReference<View>> VIEWS = new ArrayList<>();
    private static WeakReference<obZ> selection = new WeakReference<>(null);

    @Hook(target = aYg.class, method = "FzB(LkbG;)V", at = At.ENTRY, id = "track-device-selection")
    public static void selected(@Arg(0) kbG next) {
        selection = new WeakReference<>(next instanceof obZ devices ? devices : null);
    }

    @Hook(target = OCK.class, method = "cfM(Ljava/util/List;)V", at = At.BEFORE_RETURN, id = "device-state-property")
    public static void documentFields(@This OCK schema, @Arg(0) List<deN> fields) { NativeStorage.declare(schema, fields); }

    @Hook(target = dee.class, method = "cfM(Ljava/util/List;)V", at = At.BEFORE_RETURN, id = "note-state-property")
    public static void noteFields(@This dee schema, @Arg(0) List<deN> fields) { SequenceStorage.declare(schema, fields, true); }

    @Hook(target = Jzt.class, method = "cfM(Ljava/util/List;)V", at = At.BEFORE_RETURN, id = "sequence-curve-property")
    public static void curveFields(@This Jzt schema, @Arg(0) List<deN> fields) { SequenceStorage.declare(schema, fields, false); }

    @Hook(target = HEL.class, method = "cfM(Liav;)[B", at = At.REPLACE_CALL, id = "encode-note-states",
            callOwner = HEL.class, call = "cfM(Ljava/util/List;Liav;)V")
    public static void encodeStates(List events, iav content) { NativeSequence.encode(events, content); }

    @Hook(target = HEL.class, method = "cfM(LlSj;)[B", at = At.ENTRY, id = "refresh-note-state-curves")
    public static void noteContentChanged(@Arg(0) lSj content) { NativeSequence.notesEncoded(content); }

    // Three calls inside the note painter, in the order it makes them: the note, its frame, and the last stroke.
    // The state number is drawn after that stroke. Bitwig's bytecode offers no single point with all three.
    @Hook(target = bro.class, method = "cfM(Lcom/bitwig/graphics/SYo;LIbv;LkPd;DDDDDIJJZZZZZ)V", at = At.REPLACE_CALL, id = "note-state-note",
            callOwner = Ibv.class, call = "cfM(Ljava/util/function/Consumer;)Z", occurrence = 1)
    public static boolean notePainting(Ibv note, java.util.function.Consumer consumer) {
        NoteLabels.note(note);
        return note.cfM(consumer);
    }

    @Hook(target = bro.class, method = "cfM(Lcom/bitwig/graphics/SYo;LIbv;LkPd;DDDDDIJJZZZZZ)V", at = At.REPLACE_CALL, id = "note-state-frame",
            callOwner = bro.class, call = "cfM(Lcom/bitwig/graphics/SYo;Lcom/bitwig/base/geom/DoubleRectangle;ZZI)V", occurrence = 2)
    public static void noteFrame(bro painter, SYo graphics, DoubleRectangle frame, boolean first, boolean second, int corners) {
        NoteLabels.frame(painter, frame);
        painter.cfM(graphics, frame, first, second, corners);
    }

    @Hook(target = bro.class, method = "cfM(Lcom/bitwig/graphics/SYo;LIbv;LkPd;DDDDDIJJZZZZZ)V", at = At.REPLACE_CALL, id = "note-state-label",
            callOwner = SYo.class, call = "hbP()V", occurrence = 2)
    public static void noteStroked(SYo graphics) {
        graphics.hbP();
        NoteLabels.paint(graphics);
    }

    // Route only our field through added instance storage. All existing native string properties keep their dispatch.
    @Hook(target = dfg.class, method = "TMF(Lcom/bitwig/ramona/core/oDc;)Ljava/lang/String;", at = At.REPLACE_CALL,
            callOwner = deS.class, call = "FzB(Lcom/bitwig/ramona/core/OLR;I)Ljava/lang/String;", id = "read-device-states")
    public static String readField(deS schema, OLR object, int field) {
        if (SequenceStorage.owns(object, field)) return SequenceStorage.value(object);
        return NativeStorage.owns(object, field) ? NativeStorage.value(object, field) : schema.FzB(object, field);
    }

    @Hook(target = dfg.class, method = "cfM(Lcom/bitwig/ramona/core/oDc;Ljava/lang/String;)V", at = At.REPLACE_CALL,
            callOwner = deS.class, call = "cfM(Lcom/bitwig/ramona/core/OLR;ILjava/lang/String;)V", id = "edit-device-states")
    public static void editField(deS schema, OLR object, int field, String value) {
        if (SequenceStorage.owns(object, field)) SequenceStorage.set(object, field, value, true);
        else if (NativeStorage.owns(object, field)) NativeStorage.set(object, field, value, true);
        else schema.cfM(object, field, value);
    }

    @Hook(target = dfg.class, method = "MRY(Lcom/bitwig/ramona/core/oDc;Ljava/lang/String;)V", at = At.REPLACE_CALL,
            callOwner = deS.class, call = "Hnt(Lcom/bitwig/ramona/core/OLR;ILjava/lang/String;)V", id = "restore-device-states")
    public static void restoreField(deS schema, OLR object, int field, String value) {
        if (SequenceStorage.owns(object, field)) SequenceStorage.set(object, field, value, true);
        else if (NativeStorage.owns(object, field)) NativeStorage.set(object, field, value, true);
        else schema.Hnt(object, field, value);
    }

    @Hook(target = dfg.class, method = "Hnt(Lcom/bitwig/ramona/core/oDc;Ljava/lang/String;)V", at = At.REPLACE_CALL,
            callOwner = deS.class, call = "MRY(Lcom/bitwig/ramona/core/OLR;ILjava/lang/String;)V", id = "initialize-device-states")
    public static void initializeField(deS schema, OLR object, int field, String value) {
        if (SequenceStorage.owns(object, field)) SequenceStorage.set(object, field, value, false);
        else if (NativeStorage.owns(object, field)) NativeStorage.set(object, field, value, false);
        else schema.MRY(object, field, value);
    }

    @Hook(target = dfg.class, method = "cfM(Lcom/bitwig/ramona/core/oDc;Lfoz;)V", at = At.REPLACE_CALL,
            callOwner = deS.class, call = "TMF(Lcom/bitwig/ramona/core/OLR;ILfoz;)V", id = "observe-device-states")
    public static void addListener(deS schema, OLR object, int field, foz listener) {
        if (SequenceStorage.owns(object, field)) SequenceStorage.listen(object, listener, true);
        else if (NativeStorage.owns(object, field)) NativeStorage.listen(object, field, listener, true);
        else schema.TMF(object, field, listener);
    }

    @Hook(target = dfg.class, method = "MRY(Lcom/bitwig/ramona/core/oDc;Lfoz;)V", at = At.REPLACE_CALL,
            callOwner = deS.class, call = "hUR(Lcom/bitwig/ramona/core/OLR;ILfoz;)V", id = "unobserve-device-states")
    public static void removeListener(deS schema, OLR object, int field, foz listener) {
        if (SequenceStorage.owns(object, field)) SequenceStorage.listen(object, listener, false);
        else if (NativeStorage.owns(object, field)) NativeStorage.listen(object, field, listener, false);
        else schema.hUR(object, field, listener);
    }

    @Override public void declare(Blocks blocks) {
        NativeSequence.initialize();
        NoteInspector.declare(blocks);
        blocks.block("states", "Lock States").in(Context.DEVICE).content(context -> {
            if (!(context.panel() instanceof aJc panel) || !(panel.vpw() instanceof obZ devices)) {
                return Rows.text(context, null, () -> "Select exactly one native device.");
            }
            xlG selected = single(devices);
            if (selected == null) return Rows.text(context, null, () -> "Select exactly one native device.");
            xlG device = NativeDevice.owner(selected);
            if (!NativeDevice.supported(device)) return Rows.text(context, null, () -> "The parent must be a supported native device.");
            Session session = session(device);
            try { session.load(device); }
            catch (RuntimeException error) { return Rows.text(context, null, () -> error.getMessage()); }
            return new View(context, selected, device, session).content();
        });
    }

    private static xlG single(obZ devices) {
        List<?> items = devices == null ? List.of() : devices.bJ_(); // selected device objects
        return items.size() == 1 && items.getFirst() instanceof xlG device ? device : null;
    }

    private static Session session(xlG device) { return SESSIONS.computeIfAbsent(device, ignored -> new Session()); }

    static void shortcut(String operation, Object parent) {
        NativeDevice.later(() -> {
            safely(() -> {
                xlG selected = single(selection.get());
                if (selected == null) throw new IllegalArgumentException("Select one native device");
                xlG device = NativeDevice.owner(selected);
                if (!NativeDevice.supported(device)) throw new IllegalArgumentException("The parent must be a supported native device");
                if (!(parent instanceof aCp window) || window.Dah() == null || window.Dah().mtS() != device.apV()) {
                    throw new IllegalArgumentException("Select the device again in this project window");
                }
                recall(device, session(device), operation.equals("previous") ? -1 : operation.equals("next") ? 1 : 0);
            });
            refresh();
        });
    }

    private static StateBank.State state(DeviceStates data) {
        if (data == null) throw new IllegalArgumentException("Capture a state first");
        return data.snapshots().states().stream().filter(s -> s.id().equals(data.selected())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select a state first"));
    }

    private static void store(xlG device, Session session, DeviceStates data, String label) {
        store(device, session, data, label, null);
    }

    private static void store(xlG device, Session session, DeviceStates data, String label, NativeDevice.Read read) {
        String encoded = data.encode(); // validate the whole payload before opening an Undo step
        NativeDevice.transaction(device, label, () -> {
            if (read == null) write(device, encoded);
            else NativeDevice.withIdentities(read, () -> write(device, encoded));
        });
        session.load(device);
        session.status = "Saved with the project";
    }

    private static void write(xlG device, String encoded) {
        String before = NativeStorage.read(device);
        try {
            NativeStorage.write(device, encoded);
            if (!NativeStorage.read(device).equals(encoded)) throw new IllegalStateException("Bitwig did not accept the device states");
        } catch (RuntimeException | LinkageError error) {
            try { NativeStorage.write(device, before); }
            catch (RuntimeException | LinkageError rollback) { error.addSuppressed(rollback); }
            throw error;
        }
    }

    private static void recall(xlG device, Session session, int direction) {
        session.load(device);
        DeviceStates data = session.data;
        recallAt(device, session, data.snapshots().states().indexOf(state(data)) + direction);
    }

    /** Recalls by list position. A manual recall takes over from note playback, which is switched off first. */
    private static void recallAt(xlG device, Session session, int next) {
        session.load(device);
        if (session.data == null) throw new IllegalArgumentException("Capture a state first");
        if (next < 0 || next >= session.data.snapshots().states().size()) return;
        boolean paused = session.data.notePlayback();
        if (paused) {
            store(device, session, session.data.playback(false), "Pause Note State Playback");
            NativeSequence.invalidate();
        }
        DeviceStates data = session.data;
        NativeDevice.Read read = NativeDevice.read(device);
        StateBank bank = read.upgrade(data.snapshots());
        var wanted = bank.states().get(next);
        String encoded = data.with(bank, wanted.id()).encode();
        var result = Recall.apply(bank, wanted, read.layout(), read.targets(), NativeDevice.automationWriting(device),
                (label, edit) -> NativeDevice.transaction(device, label, () -> NativeDevice.withIdentities(read, edit)), () -> write(device, encoded));
        session.load(device);
        session.status = wanted.name() + " · " + result.applied() + " params"
                + (result.skipped() > 0 ? " · " + result.skipped() + " skipped" : "") + (paused ? " · Follow Notes off" : "");
    }

    private static void safely(Runnable action) {
        try { action.run(); }
        catch (RuntimeException | LinkageError error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            MOD.log(message, error);
            Notifications.post(Severity.WARNING, "Lock States", message);
        }
    }

    private static void refresh() {
        VIEWS.removeIf(reference -> reference.get() == null);
        for (WeakReference<View> reference : List.copyOf(VIEWS)) {
            View view = reference.get();
            if (view != null) safely(view::refresh);
        }
    }

    private static final class Session {
        String encoded;
        DeviceStates data;
        String status = "";

        void load(xlG device) {
            String next = NativeStorage.read(device);
            if (next.equals(encoded)) return;
            DeviceStates decoded = next.isEmpty() ? null : DeviceStates.decode(next);
            data = decoded; encoded = next; // malformed or future data is never replaced with an empty list
        }
    }

    private static final class View {
        final BlockContext context;
        final WeakReference<xlG> device;
        final WeakReference<xlG> selected;
        final Session session;
        final List<Runnable> updaters = new ArrayList<>();
        Rows.Choice states;

        View(BlockContext context, xlG selected, xlG device, Session session) {
            this.context = context; this.device = new WeakReference<>(device); this.session = session;
            this.selected = new WeakReference<>(selected);
            WeakReference<View> weak = new WeakReference<>(this);
            VIEWS.add(weak);
            WeakReference<xlG> owner = this.device;
            foz[] listener = {null};
            listener[0] = ignored -> {
                if (weak.get() == null) {
                    xlG root = owner.get();
                    if (root != null) NativeStorage.listen(root, listener[0], false);
                    return;
                }
                NativeDevice.later(() -> {
                    View view = weak.get();
                    if (view != null) safely(view::refresh); // follows native Undo/Redo while the Inspector is open
                });
            };
            NativeStorage.listen(device, listener[0], true);
        }

        Object content() {
            var rows = new ArrayList<Object>();
            rows.add(Rows.when(context, () -> selected.get() != device.get(),
                    Rows.text(context, null, () -> device.get() == null ? "" : "States of " + device.get().aG_())));
            for (int slot = 0; slot < Ui.SLOTS; slot++) {
                int index = slot;
                rows.add(Rows.when(context, () -> index < count(), button(() -> label(index),
                        () -> recallAt(current(), session, index), () -> index < count())));
            }
            states = Rows.choice(context, "More", stateLabels(), this::stateIndex, index -> NativeDevice.later(() -> {
                safely(() -> { if (index != stateIndex()) recallAt(current(), session, index); });
                refresh();
            }));
            rows.add(Rows.when(context, () -> count() > Ui.SLOTS, states.row()));
            rows.add(Rows.when(context, () -> count() == 0, Rows.text(context, null, () -> "No states yet")));
            rows.add(Ui.pair(button(() -> "New", () -> {
                        xlG root = current();
                        var read = NativeDevice.read(root, true);
                        DeviceStates data = session.data == null ? DeviceStates.empty(read.layout()) : session.data;
                        var bank = read.upgrade(data.snapshots());
                        int number = 1;
                        var names = bank.states().stream().map(StateBank.State::name).toList();
                        while (names.contains("State " + number)) number++;
                        var captured = StateBank.capture("State " + number, Recall.capture(read.targets()), read.layout());
                        var next = new ArrayList<>(bank.states()); next.add(captured);
                        store(root, session, data.with(bank.withStates(next), captured.id()), "Capture Lock State", read);
                        session.status = captured.name() + " · " + captured.values().size() + " params";
                    }, () -> count() < StateBank.MAX_STATES),
                    button(() -> "Update", () -> {
                        var old = state(session.data);
                        var read = NativeDevice.read(current(), true);
                        replace(new StateBank.State(old.id(), old.name(), Recall.capture(read.targets()), read.layout()), read.upgrade(session.data.snapshots()), read);
                        session.status = old.name() + " updated";
                    }, this::hasState)));
            rows.add(Ui.pair(button(() -> "Rename…", () -> {
                        var old = state(session.data);
                        NameDialog.show(context.panel(), "Rename State", old.name(), name -> safely(() -> {
                            current(); requireSelected(old.id());
                            var selected = state(session.data);
                            replace(new StateBank.State(old.id(), name, selected.values(), selected.layout()));
                            refresh();
                        }));
                    }, this::hasState),
                    button(() -> "Delete…", () -> {
                        var old = state(session.data);
                        ConfirmDialog.show(context.panel(), "Delete Lock State?", old.name(), () -> safely(() -> {
                            xlG root = current(); requireSelected(old.id());
                            var data = session.data; var bank = data.snapshots();
                            var next = new ArrayList<>(bank.states()); int index = next.indexOf(state(data)); next.remove(index);
                            // Removing the last state releases the old layout, so the next Capture can use a changed device.
                            if (next.isEmpty()) NativeDevice.transaction(root, "Delete Lock State", () -> write(root, ""));
                            else store(root, session, data.with(bank.withStates(next), next.get(Math.min(index, next.size() - 1)).id()), "Delete Lock State");
                            session.load(root); refresh();
                        }));
                    }, this::hasState)));
            rows.add(button(() -> "Follow Notes: " + (session.data != null && session.data.notePlayback() ? "On" : "Off"), () -> {
                        xlG root = current(); var data = session.data;
                        store(root, session, data.playback(!data.notePlayback()), "Toggle Note State Playback");
                        if (!data.notePlayback()) NativeDevice.resumeNoteAutomation(root);
                        NativeSequence.invalidate();
                        session.status = "";
                    }, () -> session.data != null));
            rows.add(Rows.when(context, () -> !session.status.isEmpty(), Rows.text(context, null, () -> session.status)));
            return Rows.column(rows.toArray());
        }

        Object button(java.util.function.Supplier<String> label, Runnable action, BooleanSupplier available) {
            return Ui.button(updaters, label, available, () -> NativeDevice.later(() -> {
                safely(() -> { current(); if (available.getAsBoolean()) action.run(); });
                refresh();
            }));
        }

        int count() { return session.data == null ? 0 : session.data.snapshots().states().size(); }

        String label(int index) {
            if (index >= count()) return "";
            var state = session.data.snapshots().states().get(index);
            return (state.id().equals(session.data.selected()) ? Ui.ACTIVE : "") + state.name();
        }

        boolean hasState() { return session.data != null && !session.data.selected().isEmpty(); }

        xlG current() {
            xlG root = device.get();
            if (root == null || !(context.panel() instanceof aJc panel) || !(panel.vpw() instanceof obZ devices)
                    || single(devices) != selected.get() || selected.get() == null || NativeDevice.owner(selected.get()) != root || root.apV() == null) {
                throw new IllegalArgumentException("The selected device changed. Reopen its Inspector.");
            }
            session.load(root);
            return root;
        }

        void requireSelected(String id) {
            if (!state(session.data).id().equals(id)) throw new IllegalArgumentException("The state selection changed");
        }

        void replace(StateBank.State updated) {
            replace(updated, session.data.snapshots(), null);
        }

        void replace(StateBank.State updated, StateBank bank, NativeDevice.Read read) {
            xlG root = current();
            var data = session.data; var next = new ArrayList<>(bank.states());
            int index = java.util.stream.IntStream.range(0, next.size()).filter(i -> next.get(i).id().equals(data.selected())).findFirst().orElseThrow();
            next.set(index, updated);
            store(root, session, data.with(bank.withStates(next), updated.id()), "Edit Lock State", read);
        }

        String[] stateLabels() {
            var bank = session.data == null ? null : session.data.snapshots();
            return bank == null || bank.states().isEmpty() ? new String[]{"No states"}
                    : java.util.stream.IntStream.range(0, bank.states().size()).mapToObj(i -> (i + 1) + ". " + bank.states().get(i).name()).toArray(String[]::new);
        }

        int stateIndex() {
            if (session.data == null) return 0;
            var data = session.data;
            for (int i = 0; i < data.snapshots().states().size(); i++) if (data.snapshots().states().get(i).id().equals(data.selected())) return i;
            return 0;
        }

        void refresh() {
            if (states == null) return;
            try { current(); } catch (IllegalArgumentException stale) { return; }
            updaters.forEach(Runnable::run); states.options(stateLabels()); Rows.refresh(context);
        }
    }
}
