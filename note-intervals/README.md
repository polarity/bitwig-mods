# Piano Roll: Note Intervals

Tester build for Bitwig Studio 6.1.7 on Windows x64 with Patchbay 0.36.0-dev.14. Enable Framework: Actions and Menus, Framework: Notifications, and Framework: Piano Roll.

In **Settings → Shortcuts**, find **Note Intervals** and assign keys to the actions you want. The following Windows shortcuts are suggestions. All six actions start unassigned.

| Action | Semitones | Suggested shortcut |
| --- | ---: | --- |
| Transpose a Fifth Up | +7 | Alt+W |
| Transpose a Fifth Down | −7 | Alt+S |
| Duplicate a Fifth Up | +7 | Shift+Alt+W |
| Duplicate a Fifth Down | −7 | Shift+Alt+S |
| Duplicate an Octave Up | +12 | Shift+Alt+E |
| Duplicate an Octave Down | −12 | Shift+Alt+D |

Select one or more notes in the Detail Editor and press the assigned shortcut. Transpose moves the selection. Duplicate adds copies at the original times with the same lengths, channels, and note data, including expressions. The original notes stay selected so you can add another interval from the same notes. Each edit is one Undo step.

Duplicate skips destinations outside MIDI range or with an overlapping note of the same pitch and channel in the same clip. Transpose refuses the whole selection when a destination would go out of range or overlap a note at its resulting position.
