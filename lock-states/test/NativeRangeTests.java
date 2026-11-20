package com.bitwig.flt.control_surface.values;

import bitwig.obf.aQ; // native indexed choice with an explicit numeric value
import bitwig.obf.aX; // native indexed range, preserving choice order
import bitwig.obf.bd; // native bounded integer range
import bitwig.obf.QQe; // indexed range interface
import bitwig.obf.VR3; // integer parameter interface
import bitwig.obf.zKO; // indexed parameter interface
import bitwig.obf.aI; // Boolean range
import bitwig.obf.Fbf; // Boolean parameter interface
import com.bitwig.mods.lockstates.BankStore;
import com.bitwig.mods.lockstates.IntegerRange;
import com.bitwig.mods.lockstates.StateBank;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Headless canaries against the real reference-build range/controller classes.
 * The mod kit translates the typed references below; this test is never shipped in helper.jar.
 * Parameter proxies supply values without opening an application, project or audio engine.
 */
public final class NativeRangeTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        orderedChoices(new int[]{0, -1, -2});
        orderedChoices(new int[]{0, -12, -24});
        orderedChoices(new int[]{10, -10, 0});
        integers(-8, 8);
        integers(Integer.MIN_VALUE, Integer.MAX_VALUE);
        integers(0, Integer.MAX_VALUE);
        integers(4, 4);
        booleans(false);
        booleans(true);
        System.out.println("LOCK_STATES_NATIVE_RANGES_OK checks=" + checks);
    }

    private static void booleans(boolean initial) throws Exception {
        var range = aI.Hnt(initial);
        boolean[] current = {initial};
        Values values = new Values();
        Fbf atom = values.proxy(Fbf.class);
        values.bind(() -> atom.MJ(), () -> range);
        values.bind(() -> atom.Grk(), () -> current[0]);
        RangedValueTarget target = new BooleanValueAtomValueTarget(atom);
        var descriptor = new StateBank.Parameter("switch", "Switch", "discrete", target.discreteValueCount(),
                target.normalizedValueToValue(0), target.normalizedValueToValue(1), List.of(target.discreteValueNames()));
        check(descriptor.steps() == 2 && descriptor.minimum() == 0 && descriptor.maximum() == 1, "native Boolean controller declares Off/On endpoints");
        var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "native-boolean-canary")), List.of(descriptor));
        for (boolean next : new boolean[]{false, true}) {
            current[0] = next;
            double normalized = target.getCurrentNormalizedValue();
            check(normalized == (next ? 1 : 0), "native Boolean normalization retains the switch value");
            var bank = StateBank.empty("Switches", layout).withStates(List.of(StateBank.capture("Switch", Map.of("switch", normalized))));
            double stored = BankStore.decode(BankStore.encode(bank)).states().getFirst().values().get("switch");
            check(target.normalizedValueToValue(stored) == (next ? 1 : 0), "native Boolean switches survive snapshot serialization");
        }
    }

    private static void orderedChoices(int[] rawValues) throws Exception {
        aX range = aX.IwV(rawValues[0]); // create a native indexed range with this initial value
        for (int raw : rawValues) range.MRY(aQ.cfM(raw, Integer.toString(raw))); // add choices in their native order
        int[] current = {rawValues[0]};
        Values values = new Values();
        zKO atom = values.proxy(zKO.class);
        values.bind(() -> atom.ODA(), () -> range); // indexed range getter
        values.bind(() -> atom.TMF(), () -> current[0]); // current raw choice getter
        RangedValueTarget target = new IndexedValueAtomValueTarget(atom);
        var descriptor = new StateBank.Parameter("OCTAVE", "Octave", "discrete", target.discreteValueCount(),
                target.normalizedValueToValue(0), target.normalizedValueToValue(1), List.of(target.discreteValueNames()));
        var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "native-choice-canary")), List.of(descriptor));
        check(descriptor.minimum() == rawValues[0] && descriptor.maximum() == rawValues[rawValues.length - 1],
                "native choice endpoint order");
        for (int index = 0; index < rawValues.length; index++) {
            current[0] = rawValues[index];
            double normalized = target.getCurrentNormalizedValue();
            check(normalized == index / (double) (rawValues.length - 1), "native choice normalization");
            var bank = StateBank.empty("Native choices", layout).withStates(List.of(
                    StateBank.capture("Choice", Map.of("OCTAVE", normalized))));
            bank = BankStore.decode(BankStore.encode(bank));
            double stored = bank.states().getFirst().values().get("OCTAVE");
            check(target.normalizedValueToValue(stored) == current[0], "native choice survives bank serialization");
        }
    }

    private static void integers(int minimum, int maximum) throws Exception {
        bd nativeRange = bd.cfM(minimum, maximum, minimum); // native integer range and initial value
        int[] current = {minimum};
        Values values = new Values();
        VR3 atom = values.proxy(VR3.class);
        values.bind(() -> atom.Grk(), () -> nativeRange); // integer range getter
        values.bind(() -> atom.hUR(), () -> current[0]); // current raw integer getter
        RangedValueTarget target = new IntegerValueAtomValueTarget(atom);
        var declared = atom.Grk();
        IntegerRange range = new IntegerRange(declared.UNj(), declared.TMF()); // same declared bounds as the mod adapter
        check(range.steps() == (long) maximum - minimum + 1, "native integer cardinality without overflow");
        var descriptor = new StateBank.Parameter("integer", "Integer", "discrete", range.steps(), minimum, maximum, List.of());
        var layout = new StateBank.Layout(List.of(new StateBank.Node("root", "native-integer-canary")), List.of(descriptor));
        for (int raw : new int[]{minimum, maximum, 0, -1, 1}) {
            if (raw < minimum || raw > maximum) continue;
            current[0] = raw;
            double normalized = range.normalized(target.getCurrentValue());
            var bank = StateBank.empty("Native integer", layout).withStates(List.of(
                    StateBank.capture("Integer", Map.of("integer", normalized))));
            bank = BankStore.decode(BankStore.encode(bank));
            check(range.raw(bank.states().getFirst().values().get("integer")) == raw, "native integer survives bank serialization");
        }
    }

    /** Record typed getter calls so no obfuscated member name is embedded in a reflection string. */
    private static final class Values implements InvocationHandler {
        final Map<Method, Supplier<?>> getters = new HashMap<>();
        boolean recording;
        Method observed;

        <T> T proxy(Class<T> type) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, this));
        }

        void bind(Runnable getter, Supplier<?> value) {
            observed = null; recording = true;
            try { getter.run(); } finally { recording = false; }
            if (observed == null) throw new AssertionError("Getter was not invoked");
            getters.put(observed, value);
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            if (recording) observed = method;
            else {
                Supplier<?> getter = getters.get(method);
                if (getter != null) return getter.get();
                throw new AssertionError("Unexpected native parameter getter: " + method);
            }
            Class<?> type = method.getReturnType();
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == double.class) return 0.0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            return null;
        }
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
        checks++;
    }
}
