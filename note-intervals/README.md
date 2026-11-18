# Piano Roll: Note Intervals

Tester build for Patchbay 0.36.0-dev.16. Enable Framework: Actions and Menus, Framework: Notifications, and Framework: Piano Roll. The ZIP uses Windows Bitwig Studio 6.1.4 as its reference build so Patchbay can translate it to Windows and macOS 6.1.7. Offline preparation is checked on both 6.1.7 targets. Live testing of this port is pending.

In **Settings → Shortcuts**, assign keys under **Notes: Duplicate by Interval** and **Notes: Transpose by Interval**. Existing assignments carry over when updating the mod. The following Windows shortcuts are suggestions.

| Action | Semitones | Suggested shortcut |
| --- | ---: | --- |
| Transpose a Fifth Up | +7 | Alt+W |
| Transpose a Fifth Down | −7 | Alt+S |
| Duplicate a Fifth Up | +7 | Shift+Alt+W |
| Duplicate a Fifth Down | −7 | Shift+Alt+S |
| Duplicate an Octave Up | +12 | Shift+Alt+E |
| Duplicate an Octave Down | −12 | Shift+Alt+D |
| Duplicate by Interval… | Enter a signed interval | |

Every interval from a semitone to an octave has **Duplicate** and **Transpose** actions for up and down. Use Bitwig's built-in transpose actions for a semitone and an octave. Search by name or by the signed value displayed on the action. For example, `+4` finds **Duplicate a Major Third Up (+4)** and **Transpose a Major Third Up (+4)**.

For **Duplicate by Interval…** and **Transpose by Interval…**, select notes, press the assigned shortcut, enter a whole number of semitones, and press Enter. For example, use `+4` for a major third up or `-3` for a minor third down. Values from `-127` to `+127` are accepted except zero. Press Escape to cancel.

Select one or more notes in the Detail Editor and press the assigned shortcut. Transpose moves the selection. Duplicate adds copies at the original times with the same lengths, channels, and note data, including expressions. The original notes stay selected so you can add another interval from the same notes. Each edit is one Undo step.

Duplicate skips destinations outside MIDI range or with an overlapping note of the same pitch and channel in the same clip. Transpose refuses the whole selection when a destination would go out of range or overlap a note at its resulting position.


Chord actions are available in [Piano Roll: Chord Tools](../chord-tools/README.md). When updating from a tester build with both sets of actions, import the two new ZIPs and enable both mods before starting Bitwig to keep the existing shortcut assignments.
