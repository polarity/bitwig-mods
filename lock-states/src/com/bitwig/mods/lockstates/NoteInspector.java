package com.bitwig.mods.lockstates;

import bml.inspector.BlockContext;
import bml.inspector.Blocks;
import bml.inspector.Context;
import bml.inspector.Rows;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.notifications.Notifications;
import bml.notifications.Severity;
import bitwig.obf.n1; // native note event
import bitwig.obf.pBi; // note clip event
import com.bitwig.flt.document.core.master.device.xlG;
import java.util.List;
import java.lang.ref.WeakReference;

final class NoteInspector {
    static void declare(Blocks blocks) {
        blocks.block("note-states", "Lock States").in(Context.NOTE).content(context -> {
            try {
                var notes = selected(context);
                if (notes.isEmpty() || !(notes.getFirst().clip().bitwigClip() instanceof pBi clip)) return Rows.text(context, null, () -> "Select notes in one clip.");
                var owners = NativeDevice.roots(clip.lDU()).stream().filter(NativeDevice::supported)
                        .filter(device -> !NativeStorage.read(device).isEmpty()).toList();
                if (owners.isEmpty()) return Rows.column(Rows.text(context, null, () -> "Capture states on a parent device on this track first."),
                        Rows.button(context, "Clear Note State Assignments", () -> run(context, () -> NativeSequence.clear(selected(context)))));
                return new View(context, notes, owners).content();
            } catch (RuntimeException failure) { return Rows.text(context, null, () -> failure.getMessage()); }
        });
    }

    private static List<Note> selected(BlockContext context) {
        var selection = PianoRoll.selection(context.panel());
        if (selection.hasOtherItems() || selection.notes().isEmpty()) return List.of();
        var notes = selection.notes(); var clip = notes.getFirst().clip();
        if (clip == null || notes.stream().anyMatch(note -> !clip.equals(note.clip()))) throw new IllegalArgumentException("Select notes in one clip");
        return notes.stream().distinct().toList();
    }

    private static void run(BlockContext context, Runnable edit) {
        NativeDevice.later(() -> {
            try { edit.run(); Rows.refresh(context); }
            catch (RuntimeException | LinkageError failure) {
                LockStates.MOD.log("Assign note state", failure);
                Notifications.post(Severity.WARNING, "Lock States", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
            }
        });
    }

    private static final class View {
        final BlockContext context;
        final List<Note> shown;
        final List<WeakReference<xlG>> owners;
        final List<Runnable> updaters = new java.util.ArrayList<>();
        int ownerIndex;
        Rows.Choice states;
        String encoded; DeviceStates decoded; // one decode per stored value, not per label
        String assigned; // state id on the shown notes, "" for none, null for mixed

        View(BlockContext context, List<Note> notes, List<xlG> devices) {
            this.context = context; shown = notes; owners = devices.stream().map(WeakReference::new).toList();
            assigned = assignment();
        }
        xlG owner() {
            var owner = owners.get(ownerIndex).get();
            if (owner == null) throw new IllegalArgumentException("The state owner is no longer available");
            return owner;
        }
        DeviceStates data() {
            String stored = NativeStorage.read(owner());
            if (!stored.equals(encoded)) { decoded = DeviceStates.decode(stored); encoded = stored; }
            return decoded;
        }
        List<StateBank.State> list() { return data().snapshots().states(); }
        int count() { return list().size(); }
        String[] labels() { return list().stream().map(StateBank.State::name).toArray(String[]::new); }
        int assignedIndex() {
            for (int i = 0; i < count(); i++) if (list().get(i).id().equals(assigned)) return i;
            return -1;
        }
        String label(int index) {
            if (index >= count()) return "";
            var state = list().get(index);
            return (state.id().equals(assigned) ? Ui.ACTIVE : "") + state.name();
        }
        List<Note> current() {
            var notes = selected(context);
            if (notes.size() != shown.size() || !notes.containsAll(shown)) throw new IllegalArgumentException("The selected notes changed. Reopen the Note Inspector.");
            return notes;
        }
        void update() {
            try { assigned = assignment(); } catch (RuntimeException stale) { assigned = null; }
            updaters.forEach(Runnable::run);
            if (states != null) states.options(labels());
            Rows.refresh(context);
        }
        void edit(Runnable change) {
            NativeDevice.later(() -> {
                try { change.run(); }
                catch (RuntimeException | LinkageError failure) {
                    LockStates.MOD.log("Assign note state", failure);
                    Notifications.post(Severity.WARNING, "Lock States", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
                }
                try { update(); } catch (RuntimeException | LinkageError stale) { LockStates.MOD.log("Refresh note states", stale); }
            });
        }
        void assign(int index) {
            edit(() -> {
                var notes = current();
                if (index < 0 || index >= count()) throw new IllegalArgumentException("Choose a state");
                NativeSequence.bind(notes, owner(), list().get(index).id(), false);
            });
        }
        Object content() {
            var rows = new java.util.ArrayList<Object>();
            var devices = Rows.choice(context, "Device", owners.stream().map(reference -> reference.get() == null ? "Unavailable" : reference.get().aG_()).toArray(String[]::new),
                    () -> ownerIndex, index -> { ownerIndex = index; update(); });
            rows.add(Rows.when(context, () -> owners.size() > 1, devices.row()));
            for (int slot = 0; slot < Ui.SLOTS; slot++) {
                int index = slot;
                rows.add(Rows.when(context, () -> index < count(),
                        Ui.button(updaters, () -> label(index), () -> index < count(), () -> assign(index))));
            }
            states = Rows.choice(context, "More", labels(), () -> Math.max(0, assignedIndex()), this::assign);
            rows.add(Rows.when(context, () -> count() > Ui.SLOTS, states.row()));
            rows.add(Ui.button(updaters, () -> ("".equals(assigned) ? Ui.ACTIVE : "") + "None", () -> true,
                    () -> edit(() -> NativeSequence.bind(current(), owner(), "", true))));
            rows.add(Rows.when(context, () -> assigned == null, Rows.text(context, null, () -> "Mixed states")));
            rows.add(Rows.when(context, () -> assigned != null && !assigned.isEmpty() && assignedIndex() < 0,
                    Rows.text(context, null, () -> "Deleted state (inactive)")));
            rows.add(Rows.when(context, () -> owners.size() > 1,
                    Ui.button(updaters, () -> "Clear All Devices", () -> true, () -> edit(() -> NativeSequence.clear(current())))));
            return Rows.column(rows.toArray());
        }
        /** The state id shared by the shown notes for this owner: "" for none, null when they differ. */
        String assignment() {
            String stored = NativeStorage.identity(owner());
            if (stored.isEmpty()) return "";
            String token = stored.substring(0, stored.indexOf('/'));
            var assignments = shown.stream().map(note -> NoteAssignments.decode(SequenceStorage.read((n1) note.bitwigNote())).states().getOrDefault(token, "")).distinct().toList();
            return assignments.size() == 1 ? assignments.getFirst() : null;
        }
    }
}
