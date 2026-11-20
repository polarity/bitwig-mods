package com.bitwig.mods.lockstates;

import bitwig.obf.n1; // native MIDI note
import bitwig.obf.ji2; // occurrence mode; cfM is ALWAYS
import bitwig.obf.dpB; // native recurrence period decoder
import bitwig.obf.oxl; // native playback decision; MRY is unconditional playback

/** Enabled operator switches also exist on ordinary notes; inspect their effective values. */
final class NativeNoteConditions {
    static void validate(n1 note) {
        if (note.ejd() && note.Z1C() != ji2.cfM) {
            throw new IllegalArgumentException("Set the assigned note's Occurrence to Always, or disable Occurrence, before binding a Lock State");
        }
        if (note.dnz()) {
            int period = dpB.TMF(note.OzW());
            for (int iteration = 0; iteration < period; iteration++) if (note.fJR(iteration) != oxl.MRY) {
                throw new IllegalArgumentException("Enable every Recurrence step, or disable Recurrence, before binding a Lock State");
            }
        }
        if (note.vpw() && note.ERO() < 1) {
            throw new IllegalArgumentException("Set the assigned note's Chance to 100%, or disable Chance, before binding a Lock State");
        }
    }
}
