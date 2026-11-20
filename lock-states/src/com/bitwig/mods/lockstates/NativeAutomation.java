package com.bitwig.mods.lockstates;

import bitwig.obf.LEn; // track automation lane container
import bitwig.obf.lkr; // persistent automation lane
import bitwig.obf.la1; // automation clip event
import bitwig.obf.iav; // automation content
import bitwig.obf.pBi; // source note clip
import bitwig.obf.YvI; // native timeline event
import bitwig.obf.zZW; // native lane model
import bitwig.obf.yiy; // automation lane
import bitwig.obf.btv; // launcher slot
import bitwig.obf.hMP; // automation point
import bitwig.obf.vOB; // continuous point
import bitwig.obf.jRs; // interpolation modes
import com.bitwig.flt.document.core.master.eD;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

/** The actual native lane/clip insertion path, also exercised by the packaged integration canary. */
final class NativeAutomation {
    static lkr materialize(LEn lanes, eD address) {
        // Public native API: append a persistent lane, without global arrangement
        // baseline points or selecting/expanding it in the editor. cfM(address,true)
        // instead returns an about-to-be-created Dxn placeholder that rejects clips.
        return lanes.MRY(address, false, false, false);
    }

    static la1 insert(lkr lane, pBi source, CurveSpec spec, List<la1> created) {
        var address = lane.d3();
        var content = iav.cfM(address);
        NativeEnvelope.seed(content, address.MIO(), spec);
        SequenceStorage.write(content, spec.encode());
        var curve = la1.cfM(source.x3t(), source.QWG() - source.x3t(), source.dC_(), content);
        created.add(curve); // retain even if attachment/geometry fails, so the caller can roll back
        if (source.ztx()) lane.LaE().cfM((YvI) curve, lane.LaE().cfM().size());
        else lane.ODA().FzB(source.dz_().MJ()).cfM(curve);
        curve.MRY(source);
        curve.MRY(false); // follow native note-clip timing instead of free-running
        return curve;
    }

    static void remove(lkr lane) { lane.MYI().cfM((zZW) lane, true); }

    static la1 ensure(lkr lane, pBi source, CurveSpec spec, List<la1> created) {
        var existing = find(lane, source);
        return existing == null ? insert(lane, source, spec, created) : existing;
    }

    static la1 find(yiy lane, pBi source) {
        if (lane == null) return null;
        if (!source.ztx()) {
            var slot = lane.ODA().UNj(source.dz_().MJ());
            return slot != null && slot.UNj() instanceof la1 curve ? curve : null;
        }
        for (Object item : lane.LaE().cfM()) if (item instanceof la1 curve) {
            if (curve.x3t() == source.x3t() && curve.QWG() == source.QWG()) return curve;
            if (curve.x3t() < source.QWG() && source.x3t() < curve.QWG()) throw new IllegalArgumentException("Overlapping automation clips block note states");
        }
        return null;
    }

    /** Resolves actual note-clip uses, including the shared-content safety check. */
    static List<SequencePlan.Point> render(iav content, Function<pBi, List<SequencePlan.Point>> resolve) {
        List<SequencePlan.Point> wanted = null;
        for (Object use : content.LGf()) if (use instanceof la1 automation) {
            var raw = automation.bB() instanceof pBi source ? resolve.apply(source) : List.<SequencePlan.Point>of();
            if (wanted != null && !wanted.equals(raw)) throw new IllegalArgumentException("Shared automation content has different note states in its clips. Make the clips independent before assigning states.");
            wanted = raw;
        }
        return wanted == null ? List.of() : wanted;
    }

    private record Owned(la1 curve, CurveSpec spec) { }
    /** Native repair adds HOLD baselines at zero, clip-gap starts and the final end.
     * Accept only those unchanged points on exclusively marked, pristine content.
     * We neither remove nor rewrite them, including data left by dev.9. */
    static boolean managed(yiy lane, String owner) {
        if (lane == null) return false;
        try {
            var arrangement = new ArrayList<Owned>();
            var all = new ArrayList<Owned>();
            for (Object item : lane.LaE().cfM()) if (item instanceof la1 curve) arrangement.add(owned(curve)); else return false;
            all.addAll(arrangement);
            for (Object item : lane.ODA().UZr()) if (item instanceof btv slot && slot.UNj() != null) {
                if (!(slot.UNj() instanceof la1 curve)) return false;
                all.add(owned(curve));
            }
            if (all.isEmpty()) return false;
            String token = owner == null ? all.getFirst().spec().owner() : owner;
            String key = all.getFirst().spec().parameter().key();
            if (all.stream().anyMatch(item -> !item.spec().owner().equals(token) || !item.spec().parameter().key().equals(key))) return false;
            arrangement.sort(java.util.Comparator.comparingDouble(item -> item.curve().x3t()));
            var boundaries = new ArrayList<SequencePlan.Point>();
            if (arrangement.isEmpty()) all.forEach(item -> boundaries.add(new SequencePlan.Point(0, item.spec().rawBaseline())));
            else {
                if (arrangement.getFirst().curve().x3t() > 0) boundaries.add(new SequencePlan.Point(0, arrangement.getFirst().spec().rawBaseline()));
                for (int i = 0; i < arrangement.size(); i++) {
                    var item = arrangement.get(i);
                    if (i + 1 == arrangement.size() || item.curve().QWG() < arrangement.get(i + 1).curve().x3t()) boundaries.add(new SequencePlan.Point(item.curve().QWG(), item.spec().rawBaseline()));
                }
            }
            for (Object item : lane.LaE().TMF()) {
                if (!(item instanceof hMP point)) return false;
                if (point instanceof vOB continuous && continuous.FzB() != jRs.cfM) return false;
                double raw = NativeEnvelope.raw(point);
                if (boundaries.stream().noneMatch(boundary -> boundary.time() == point.x3t() && NativeEnvelope.same(raw, boundary.value()))) return false;
            }
            return true;
        } catch (RuntimeException | LinkageError invalid) { return false; } // treat unknown/manual lanes as protected
    }

    private static Owned owned(la1 curve) {
        var spec = CurveSpec.decode(SequenceStorage.read(curve.SVQ()));
        if (!NativeEnvelope.pristine(curve.SVQ(), spec)) throw new IllegalArgumentException("Edited note state automation");
        return new Owned(curve, spec);
    }
}
