package com.bitwig.mods.lockstates;

import java.io.IOException;

/** Identifies mod-owned automation content with one unchanged native baseline sentinel. */
public record CurveSpec(int version, String owner, StateBank.Parameter parameter, double baseline, double rawBaseline) {
    public CurveSpec {
        if (version != 1 || parameter == null || !Double.isFinite(baseline) || baseline < 0 || baseline > 1 || !Double.isFinite(rawBaseline)) throw new IllegalArgumentException("Invalid note sequence curve");
        StateBank.uuid(owner);
    }
    public String encode() { return BankStore.encodeDocument(this); }
    public static CurveSpec decode(String data) {
        try { return BankStore.decode(data, CurveSpec.class); }
        catch (IOException failure) { throw new IllegalArgumentException("Cannot read the note sequence curve", failure); }
    }
}
