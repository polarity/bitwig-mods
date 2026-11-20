package com.bitwig.mods.lockstates;

import bitwig.obf.deS; // document object schema
import bitwig.obf.deN; // field descriptor
import bitwig.obf.dfg; // native serialized string property
import bitwig.obf.dee; // native MIDI note schema
import bitwig.obf.Jzt; // native automation content schema
import bitwig.obf.n1; // native MIDI note
import bitwig.obf.iav; // native automation content
import bitwig.obf.dfI; // Undo change validator
import bitwig.obf.eOU; // native property events
import bitwig.obf.foz; // native property listener
import bml.api.AddField;
import com.bitwig.ramona.core.OLR;
import com.bitwig.ramona.core.oDc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Objects;

final class SequenceStorage {
    static final int NOTE_ID = 0x4c530003, CURVE_ID = 0x4c530004;
    static final String NOTE_KEY = "polarity_lock_states_note", CURVE_KEY = "polarity_lock_states_curve";
    private static dfg note, curve;

    static synchronized void declare(deS schema, List<deN> fields, boolean notes) {
        int id = notes ? NOTE_ID : CURVE_ID;
        String key = notes ? NOTE_KEY : CURVE_KEY;
        if (fields.stream().anyMatch(field -> field.E3() == id || key.equals(field.WVl()))) throw new IllegalStateException("Conflicting Lock States sequencer field");
        dfg property = notes ? note : curve;
        if (property == null) {
            property = new dfg(key, id, notes ? dee.cfM : Jzt.cfM, "", null, 0, 65536, 410);
            property.cfM("not_engine", Boolean.TRUE);
            property.cfM("dont_send_to_slave", Boolean.TRUE);
            if (notes) note = property; else curve = property;
        }
        schema.cfM(property, fields);
    }

    static boolean owns(OLR object, int id) { return id == NOTE_ID && object instanceof n1 || id == CURVE_ID && object instanceof iav; }
    static String read(n1 object) { require(object, note, NOTE_KEY); return note.TMF(object); }
    static String read(iav object) { require(object, curve, CURVE_KEY); return curve.TMF(object); }
    static void write(n1 object, String data) { note.cfM(object, data); }
    static void write(iav object, String data) { curve.cfM(object, data); }
    private static void require(oDc object, dfg field, String key) {
        if (field == null || object.nh_().Hnt(key) != field) throw new IllegalStateException("Note state storage is not registered. Restart Bitwig.");
    }
    static String value(OLR object) {
        Object cell = Fields.SEQUENCE.get(object);
        return cell instanceof Cell stored ? stored.value : "";
    }
    static void set(OLR object, int id, String value, boolean guarded) {
        Objects.requireNonNull(value);
        if (value.length() > 65536) throw new IllegalArgumentException("Note state data exceeds its size limit");
        String before = value(object);
        if (before.equals(value)) return;
        dfg field = id == NOTE_ID ? note : curve;
        var owner = (oDc) object;
        if (guarded && dfI.Hnt) { dfI.cfM(owner, field, () -> set(object, id, value, false)); return; }
        owner.cfM(field, owner, value);
        Cell cell = cell(object); cell.value = value;
        cell.events.fireValueChanged(field.cfM(owner), value, before);
        if (object instanceof iav content && !value.isEmpty()) NativeSequence.remember(content);
        NativeSequence.invalidate(); // includes Undo/Redo of note assignments, which are deliberately not engine fields
    }
    static void listen(OLR object, foz listener, boolean add) {
        if (add) cell(object).events.addListener(listener); else cell(object).events.removeListener(listener);
    }
    private static Cell cell(OLR object) {
        Object existing = Fields.SEQUENCE.get(object);
        if (existing instanceof Cell cell) return cell;
        Cell created = new Cell(); Object previous = Fields.SEQUENCE.compareAndExchange(object, null, created);
        return previous == null ? created : (Cell) previous;
    }
    private static final class Cell { volatile String value = ""; final eOU events = new eOU(); }
    private static final class Fields {
        @AddField(target = oDc.class, name = "lock_states$sequence", type = Object.class)
        static final VarHandle SEQUENCE = handle();
        private static VarHandle handle() {
            try { return MethodHandles.privateLookupIn(oDc.class, MethodHandles.lookup()).findVarHandle(oDc.class, "lock_states$sequence", Object.class); }
            catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
        }
    }
}
