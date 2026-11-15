package com.bitwig.mods.repitchtranspose;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import bitwig.obf.EeN; // the Inspector's event selection and editing model
import bitwig.obf.JGz; // the Inspector's source-tempo parameter
import bitwig.obf.Xyf; // standard Inspector widget sizes
import bitwig.obf.aMZ; // Bitwig's Inspector base and label/value row builder
import bitwig.obf.aMo; // the note and audio-event Inspector
import bitwig.obf.dBz; // a push button
import bitwig.obf.dPc; // a numeric editor
import bitwig.obf.dXL; // a widget group
import bitwig.obf.dYI; // a horizontal group of equally sized controls
import bitwig.obf.doV; // a padded Inspector section with a themed background
import bitwig.obf.dwF; // a widget
import bitwig.obf.dwy; // a label that can bind to a live text value
import bitwig.obf.j2X; // the audio-event playback mode
import bitwig.obf.mvE; // the Inspector's playback-mode parameter
import bitwig.obf.zfc; // native control width policy
import bml.inspector.Blocks;
import bml.inspector.BuiltBlock;
import bml.inspector.InspectorProvider;

public final class RepitchTranspose implements InspectorProvider {
    private static final double MIN_BPM = 20.0;
    private static final double MAX_BPM = 9999.0;
    // Inspector callbacks run on the UI thread. Closed projects can release their events.
    private static final Map<Object, Double> REFERENCE_TEMPOS = new WeakHashMap<>();

    @Override
    public void declare(Blocks blocks) {
        blocks.onBlockBuilt(RepitchTranspose::addControls);
    }

    private static void addControls(BuiltBlock block) {
        if (!block.bitwigBlock() || !(block.panel() instanceof aMo panel)
                || !(block.widget() instanceof dXL body)
                || !block.title().equals(String.valueOf(panel.XzC().getValue()))) {
            return;
        }
        EeN events = panel.UIe();
        if (!events.BBg() || events.bK_().size() != 1) return;

        JGz tempo = events.Bqd();
        shiftText(events, tempo.fi_()); // capture the reference before the first edit
        dwy shift = dwy.BBg(tempo.BBg().Gef(value ->
                value instanceof Number bpm ? shiftText(events, bpm.doubleValue()) : "—"));
        shift.zzi("Shift from this event's tempo when first inspected this session");
        dXL readout = dXL.fpX();
        readout.ocs();
        readout.IvP(shift);

        dPc amount = dPc.yAy();
        amount.beI(panel.QDQ().ppC().scr()); // same theme style as the native Tempo field
        amount.nhI(-120.0);
        amount.IvP(120.0);
        amount.zzi("Pitch shift in semitones from the current playback pitch");
        dBz apply = dBz.beI("Apply", () -> {
            if (transpose(events, amount.raV())) amount.wLf(0.0);
        });
        apply.a_(zfc.beI());
        apply.Gef(Xyf.Gef);
        apply.zzi("Apply the semitone shift to the event tempo");

        dYI input = dYI.IKw();
        input.byf(2);
        input.IvP(amount);
        input.IvP(apply);
        dwF entry = aMZ.beI(aMZ.beI("Repitch"), input);
        dYI steps = dYI.beI(step(events, -12), step(events, -1), step(events, 1), step(events, 12));
        dXL controls = dXL.unz(3);
        controls.IvP(aMZ.beI(aMZ.beI("Shift"), readout));
        controls.IvP(entry);
        controls.IvP(steps);

        mvE mode = events.GwM();
        // Use the native section's background and insets to align with the Tempo row.
        doV section = panel.Gef(controls);
        // Keep observing the mode while the section itself is hidden.
        aMZ.beI(section.Afd(), mode.BBg().sCA(j2X.BBg));
        controls.sCA(controls.Jej(), tempo.tWW()); // follow the native tempo editor's enabled state
        body.IvP(section);
    }

    private static String shiftText(EeN events, double bpm) {
        List<?> selection = events.bK_();
        if (selection.size() != 1 || !Double.isFinite(bpm) || bpm <= 0.0) return "—";
        double reference = REFERENCE_TEMPOS.computeIfAbsent(selection.getFirst(), event -> bpm);
        return formatShift(reference, bpm);
    }

    static String formatShift(double reference, double bpm) {
        double semitones = 12.0 * Math.log(reference / bpm) / Math.log(2.0);
        double rounded = Math.round(semitones * 100.0) / 100.0;
        return String.format(Locale.ROOT, rounded == 0.0 ? "%.2f st" : "%+.2f st", rounded);
    }

    private static dBz step(EeN events, int semitones) {
        String label = semitones > 0 ? "+" + semitones : Integer.toString(semitones);
        dBz button = dBz.beI(label, () -> transpose(events, semitones));
        button.zzi("Repitch by " + label + " semitones");
        return button;
    }

    private static boolean transpose(EeN events, double semitones) {
        if (!events.BBg() || events.bK_().size() != 1 || events.GwM().fj_() != j2X.BBg) return false;
        JGz tempo = events.Bqd();
        if (!tempo.ZRB() || tempo.scr()) return false; // disabled or mixed
        double current = tempo.fi_();
        double target = shiftedTempo(current, semitones);
        if (!Double.isFinite(target) || target == current) return false;

        // Match the transaction used when entering a value in Bitwig's Tempo field.
        events.beI(false);
        try {
            tempo.beI(target);
        } finally {
            events.Gef(false);
        }
        return true;
    }

    static double shiftedTempo(double bpm, double semitones) {
        if (!Double.isFinite(bpm) || bpm <= 0.0 || !Double.isFinite(semitones)) return Double.NaN;
        double shifted = bpm * Math.pow(2.0, -semitones / 12.0);
        return shifted >= MIN_BPM && shifted <= MAX_BPM ? shifted : Double.NaN;
    }
}
