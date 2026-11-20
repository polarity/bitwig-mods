package com.bitwig.mods.lockstates;

import bitwig.obf.bpM; // clip content, shared by the clips using it
import bitwig.obf.bro; // painter of a note grid
import bitwig.obf.iok; // colour
import bitwig.obf.n1; // native note event
import bitwig.obf.pBi; // note clip event
import bitwig.obf.xJo; // track
import com.bitwig.base.geom.DoubleRectangle;
import com.bitwig.graphics.SYo; // drawing surface
import com.bitwig.ramona.core.oDc;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** The state number drawn inside notes that have an assignment. */
final class NoteLabels {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean CLEAR_POSTED = new AtomicBoolean();

    private NoteLabels() { }

    // The note being painted. Painting is sequential, so one slot is enough.
    private static Object note;
    private static bro painter;
    private static DoubleRectangle frame;

    static void note(Object painted) { note = painted; painter = null; frame = null; }

    static void frame(bro by, DoubleRectangle painted) { painter = by; frame = painted == null ? null : painted.cfM(); } // a copy

    /** Runs for every painted note: unassigned notes cost one field read. Never lets a failure reach the painter. */
    static void paint(SYo graphics) {
        Object painted = note; bro by = painter; DoubleRectangle bounds = frame;
        note = null; painter = null; frame = null;
        try {
            if (by == null || bounds == null || !(painted instanceof n1 event)) return;
            String stored = SequenceStorage.read(event);
            if (stored.isEmpty()) return;
            String text = suffix(event, stored);
            if (text.isEmpty()) return;
            // Bitwig's own in-note text: its font, clipped to the note, omitted when the note is too narrow.
            by.cfM(graphics, text, bounds, bounds.Hnt() + 3, bounds.FzB(), bounds.hUR() - 2, iok.cfM, 0.5);
        } catch (RuntimeException | LinkageError failure) {
            // never disturb note painting
        }
    }

    private static String suffix(n1 note, String stored) {
        xJo track = track(note);
        if (track == null) return "";
        String key = System.identityHashCode(track) + ":" + stored;
        String cached = CACHE.get(key);
        if (cached != null) return cached;
        var numbers = new ArrayList<String>();
        for (var entry : NoteAssignments.decode(stored).states().entrySet()) {
            var owner = NativeSequence.findOwner(track, entry.getKey());
            if (owner == null) continue; // the device left this track; the binding is inactive
            var states = NativeSequence.decoded(owner, NativeStorage.read(owner)).snapshots().states();
            int index = -1;
            for (int i = 0; i < states.size(); i++) if (states.get(i).id().equals(entry.getValue())) index = i;
            numbers.add(index < 0 ? "?" : Integer.toString(index + 1)); // "?" marks a deleted state
        }
        String result = String.join("/", numbers);
        CACHE.put(key, result);
        // Valid for one UI event: renaming, deleting or reordering states shows at the next repaint.
        if (CLEAR_POSTED.compareAndSet(false, true)) NativeDevice.later(() -> { CACHE.clear(); CLEAR_POSTED.set(false); });
        return result;
    }

    /** The track of a clip using the note's content. Tokens are scoped to that track. */
    private static xJo track(n1 note) {
        oDc object = note;
        for (int depth = 0; object != null && depth < 16; depth++, object = object.K_()) {
            if (object instanceof pBi clip) return clip.lDU();
            if (object instanceof bpM content) {
                for (Object use : content.LGf()) if (use instanceof pBi clip && clip.lDU() != null) return clip.lDU();
                return null;
            }
        }
        return null;
    }
}
