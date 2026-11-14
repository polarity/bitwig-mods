# Piano Roll: Step Sequencer

Tester build. Checked with Patchbay Mod Loader 0.36.0-dev.14 and Bitwig Studio 6.1.3 on Windows x64. Enable Framework: Piano Roll, which ships with Patchbay.

Open a MIDI clip in the Detail Editor and click **Show Steps** below Drum/Hybrid. Click a step to add or remove a note. Start on an empty step and drag across the grid to draw more notes in one undo step. Select **Velocity**, **Offset**, **Length**, **Repeat**, **Pitch**, or **Chance** in the footer and drag an occupied step vertically to adjust that value. Press Escape to cancel the gesture.

The grid shows 16 sixteenth-note steps per page. Use the header arrows to change pages and the footer arrows to browse pitches or pads. The playhead highlights the current column and outlines its set steps. The bright fill shows velocity. A thin line marks a held note in a later cell. A number marks several notes starting in the same cell. These cells are protected from clicks that would ambiguously delete a note or cut a held note.

Right-drag or Shift-left-drag across the grid to erase steps. A red outline previews each step to be removed. Release to erase them in one undo step, or press Escape to cancel. The eraser skips continuation cells and cells with multiple note starts.

Vertical marks show repeats. The Repeat tab edits the number of divisions of the note, with **Off** for one hit. Editing a note with a tempo-based repeat rate switches that note to count-based repeats. The Chance tab shows the probability as a percentage and fill height. Both tabs edit Bitwig's native note operators.

Offset moves the note start within its step while preserving note length. Length changes the duration. Both use 1/64-note increments relative to the original value. Pitch transposes the note by semitones and moves it to the corresponding row. Each gesture is one undo step. Edits that would move into or extend an overlap on the same pitch and MIDI channel are refused.

Download this mod’s ZIP from the [release page](https://github.com/polarity/bitwig-mods/releases). In Patchbay, choose **Mods → Add Mod…** and select the ZIP. Enable the mod and click **Load**.
