# Piano Roll: Chord Tools

Tester build for Patchbay 0.36.0-dev.16. Enable Framework: Actions and Menus, Framework: Notifications, and Framework: Piano Roll. The ZIP uses Windows Bitwig Studio 6.1.4 as its reference build so Patchbay can translate it to Windows and macOS 6.1.7. Offline preparation is checked on both 6.1.7 targets. Live testing of this port is pending.

In **Settings → Shortcuts**, assign keys under **Notes: Chord Inversions**, **Notes: Chord Voicings**, and **Notes: Chord Quality**.

If you are upgrading a tester build of Note Intervals that bundled these chord actions, replace it with the new [Note Intervals](../note-intervals/README.md) ZIP and add the Chord Tools ZIP. Enable both mods before starting Bitwig so existing shortcut assignments carry over.

## Chord inversions

Assign **Next Inversion** and **Previous Inversion** under **Notes: Chord Inversions**. Select the notes of one or more chords in the Detail Editor. Each group of selected notes sharing a start time, clip, and MIDI channel is handled independently.

**Next Inversion** puts the lowest note one or more octaves above its original pitch, just above the highest voice. **Previous Inversion** puts the highest note just below the lowest voice in the same way. For example, `C4–E4–G4` becomes `E4–G4–C5` with Next Inversion and returns with Previous Inversion. Notes stay selected, and a single Undo reverses the edits across all chords.

Single notes and chords with a tied pitch at the edge being moved are skipped. A chord is also skipped if its destination is outside MIDI range or would overlap another note on the same pitch and channel.

## Open and close voicings

Assign **Open Voicing**, **Close Voicing**, **Open Voicing (Keep Bass)**, or **Close Voicing (Keep Bass)** under **Notes: Chord Voicings**. Use the actions on chords of at least three distinct pitches. The same start-time, clip, and MIDI channel grouping applies as for inversions.

**Open Voicing** moves one note by an octave to increase the distance between the lowest and highest notes. The result spans at least an octave, and repeated presses can spread it further. **Close Voicing** reduces the range with the smallest total pitch change it can make from the current voicing. Press it again to tighten the chord further when a closer position is available. The **Keep Bass** variants keep the original lowest note in place and below the other voices.

Only octave positions change. Notes stay selected, and one Undo reverses the edits across all chords. Chords without an available destination are skipped.

## Chord quality

Assign **Make Minor**, **Make Major**, **Make Diminished**, **Make Sus2**, or **Make Sus4** under **Notes: Chord Quality**. Select chords of at least three different pitch classes. The same start-time, clip, and MIDI channel grouping applies as for inversions.

The action recognizes common chord shapes from their pitch classes, including inversions. For equally plausible roots, choose a root in the dialog. For example, the notes of C6 and Am7 offer C and A. If several chords need a choice, the dialogs run in sequence. Cancel abandons the pending edit for the whole selection.

The root remains at its existing pitch. The action adjusts the third or suspension and the fifth as needed. It keeps extensions where possible and uses nearby available pitches for extra voices such as a split third. For example, **Make Minor** changes an inverted `E–G–C` to `E♭–G–C`, and changes `C–E–G–B♭` into `C–E♭–G–B♭`.

Only pitch changes. Timing, note count, selection, and note expressions are preserved. Unsafe conversions are skipped, and a single Undo restores all chords edited by the shortcut.
