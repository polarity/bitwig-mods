package com.bitwig.mods.lockstates;

import bitwig.obf.deN; // document field descriptor
import bitwig.obf.dfg; // native serialized string property
import bitwig.obf.dfI; // native Undo change recorder
import bitwig.obf.eOU; // native property change listeners
import bitwig.obf.foz; // native property listener
import bml.api.AddField;
import com.bitwig.flt.document.core.master.device.Dgc; // common device document object
import com.bitwig.flt.document.core.master.device.OCK; // common device schema
import com.bitwig.ramona.core.OLR;
import com.bitwig.ramona.core.oDc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Objects;

/** Adds a genuine Ramona property: serialized, cloned and recorded by Bitwig's document/Undo machinery. */
final class NativeStorage {
    static final String KEY = "polarity_lock_states";
    // A mod-owned field ID, deliberately outside the native field range. The serialized name is stable across maps.
    static final int ID = 0x4c530001;
    static final int IDENTITY_ID = ID + 1;
    static final String IDENTITY_KEY = "polarity_lock_states_identity";
    private static dfg property;
    private static dfg identity;

    static synchronized void declare(OCK schema, List<deN> fields) {
        if (fields.stream().anyMatch(field -> field.E3() == ID || field.E3() == IDENTITY_ID
                || KEY.equals(field.WVl()) || IDENTITY_KEY.equals(field.WVl()))) {
            throw new IllegalStateException("Lock States document field conflicts with another extension");
        }
        if (property == null) {
            // Same persistent, editable, observable flags as Bitwig's user_device_name property.
            property = descriptor(KEY, ID, DeviceStates.MAX_CHARACTERS);
            identity = descriptor(IDENTITY_KEY, IDENTITY_ID, 73);
        }
        schema.cfM(property, fields); // include the field in this device type's schema
        schema.cfM(identity, fields);
    }

    private static dfg descriptor(String key, int id, int maximum) {
        var descriptor = new dfg(key, id, OCK.cfM, "", null, 0, maximum, 410);
        descriptor.cfM("not_engine", Boolean.TRUE); // project/editor data, never an audio-engine parameter
        descriptor.cfM("dont_send_to_slave", Boolean.TRUE);
        return descriptor;
    }

    static boolean owns(OLR object, int field) { return (field == ID || field == IDENTITY_ID) && object instanceof Dgc; }

    static String read(oDc object) {
        return read(object, property, KEY);
    }

    static String identity(oDc object) { return read(object, identity, IDENTITY_KEY); }
    static void writeIdentity(oDc object, String value) { identity.cfM(object, value); }

    private static String read(oDc object, dfg field, String key) {
        if (object.nh_().Hnt(key) != field || field == null) { // inherited native field lookup
            throw new IllegalStateException("Lock States project storage was not registered. Restart Bitwig with the mod enabled.");
        }
        return field.TMF(object); // native property getter, routed through the schema hook
    }

    static void write(oDc object, String data) { property.cfM(object, data); } // native tracked setter

    static String value(OLR object, int field) { return field == ID ? cell(object).value : cell(object).identity; }

    static void set(OLR object, int field, String value, boolean guarded) {
        Objects.requireNonNull(value);
        if (value.length() > DeviceStates.MAX_CHARACTERS) throw new IllegalArgumentException("Device states exceed the size limit");
        Cell cell = cell(object);
        if (field == IDENTITY_ID && !value.isEmpty()) {
            String[] parts = value.split("/", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid device identity");
            StateBank.uuid(parts[0]); StateBank.uuid(parts[1]);
        }
        if (value.equals(value(object, field))) return;
        oDc owner = (oDc) object;
        dfg descriptor = field == ID ? property : identity;
        if (guarded && dfI.Hnt) { // match a generated native setter's change-validation path
            dfI.cfM(owner, descriptor, () -> set(object, field, value, false));
            return;
        }
        owner.cfM(descriptor, owner, value); // record the old value for Undo and mark the document changed
        String before = value(object, field);
        if (field == ID) cell.value = value; else cell.identity = value;
        listeners(cell, field).fireValueChanged(descriptor.cfM(owner), value, before);
        NativeSequence.invalidate(); // state updates, native Undo/Redo and identity edits refresh generated engine data
    }

    static void listen(OLR object, foz listener, boolean add) {
        listen(object, ID, listener, add);
    }

    static void listen(OLR object, int field, foz listener, boolean add) {
        if (add) listeners(cell(object), field).addListener(listener);
        else listeners(cell(object), field).removeListener(listener);
    }

    private static eOU listeners(Cell cell, int field) { return field == ID ? cell.listeners : cell.identityListeners; }

    private static Cell cell(OLR object) {
        Object existing = Fields.DATA.get(object);
        if (existing instanceof Cell cell) return cell;
        Cell created = new Cell();
        Object previous = Fields.DATA.compareAndExchange(object, null, created);
        return previous == null ? created : (Cell) previous;
    }

    private static final class Cell {
        volatile String value = "";
        volatile String identity = "";
        final eOU listeners = new eOU();
        final eOU identityListeners = new eOU();
    }

    private static final class Fields {
        @AddField(target = Dgc.class, name = "lock_states$data", type = Object.class)
        static final VarHandle DATA = handle();

        private static VarHandle handle() {
            try {
                return MethodHandles.privateLookupIn(Dgc.class, MethodHandles.lookup())
                        .findVarHandle(Dgc.class, "lock_states$data", Object.class);
            } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
        }
    }
}
