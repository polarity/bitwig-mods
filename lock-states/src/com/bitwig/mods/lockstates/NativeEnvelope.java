package com.bitwig.mods.lockstates;

import bitwig.obf.bU; // native automation value domain
import bitwig.obf.PQT; // native automation point timeline
import bitwig.obf.YvI; // timeline event
import bitwig.obf.jRs; // HOLD versus LINEAR interpolation
import bitwig.obf.HEL; // native engine event encoder
import bitwig.obf.aL; // continuous automation range
import bitwig.obf.aI; // Boolean automation range
import bitwig.obf.aX; // indexed automation range
import bitwig.obf.bd; // bounded integer automation range
import bitwig.obf.zwc; // indexed point storing the actual raw choice value
import bitwig.obf.yfJ; // integer point storing the full signed integer value
import bitwig.obf.iav; // automation content retaining the baseline sentinel
import bitwig.obf.hMP; // native point
import bitwig.obf.vOB; // continuous point with raw value getter
import java.util.List;

final class NativeEnvelope {
    static PQT timeline(bU range, List<SequencePlan.Point> raw) {
        PQT points = range.sut(); // create detached native points, never edit the project's automation model
        for (int i = 0; i < raw.size(); i++) {
            var point = raw.get(i);
            // Native factories accept normalized input. Direct discrete constructors avoid
            // reordered choices and overflowing normalization of full-width integer ranges.
            points.cfM(point(range, point.time(), point.value()), i);
        }
        return points;
    }
    private static hMP point(bU range, double time, double raw) {
        if (range instanceof aL continuous) return continuous.Hnt(time, continuous.cfM(raw), jRs.cfM, 0);
        if (range instanceof aX) return zwc.cfM(time, integer(raw));
        if (range instanceof bd) return yfJ.cfM(time, integer(raw));
        if (range instanceof aI toggle) {
            if (raw != 0 && raw != 1) throw new IllegalArgumentException("Invalid native Boolean automation value: " + raw);
            return toggle.MRY(time, raw, jRs.cfM, 0); // native oUR Boolean point; raw and normalized endpoints are both 0/1
        }
        throw new IllegalArgumentException("Unsupported native automation range: " + range.getClass().getName());
    }
    static void seed(iav content, bU range, CurveSpec spec) {
        if (content.TD2().ejd() != 0) throw new IllegalArgumentException("Cannot initialize nonempty automation content");
        content.TD2().cfM(point(range, 0, spec.rawBaseline()), 0);
    }
    static boolean pristine(iav content, CurveSpec spec) {
        var points = content.TD2();
        if (points.ejd() != 1) return false;
        var point = points.hUR(0);
        return point.x3t() == 0 && same(raw(point), spec.rawBaseline());
    }
    static double raw(hMP point) { return point instanceof vOB continuous ? continuous.cfM() : point.Ucl(); }
    static boolean same(double first, double second) { return Math.abs(first - second) <= Math.max(1e-9, Math.abs(second) * 1e-12); }
    private static int integer(double value) {
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid raw automation integer");
        return (int) value;
    }
    static void append(List output, bU range, List<SequencePlan.Point> raw) {
        HEL.cfM(output, timeline(range, raw), range); // native automation encoding for both indexed and continuous domains
    }
    static void preflight(bU range, List<SequencePlan.Point> raw, String parameter) {
        try { append(new java.util.ArrayList<>(), range, raw); }
        catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Cannot sequence '" + parameter + "' (" + range.getClass().getName() + "): " + failure.getMessage(), failure);
        }
    }
}
