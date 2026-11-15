# Inspector: Repitch Transpose

This tester mod adds a **Repitch** row below **Tempo** in the Audio Event Inspector for a single selected Repitch event. Use **−12**, **−1**, **+1** or **+12** for a quick change. For another interval, enter a signed number of semitones and click **Apply**. Each operation shifts from the event's current playback pitch.

**Shift** shows the accumulated change in semitones. It updates when you edit the event's **Tempo** directly as well. Zero is the tempo the mod first sees for that event in the current Bitwig session. The reference resets when Bitwig restarts.

The mod edits the source tempo through Bitwig's Inspector. At a fixed project tempo, the new source tempo is `current BPM × 2^(−semitones / 12)`. For an event at 173 BPM, **+1** sets the source tempo to about 163.29 BPM and **−1** sets it to about 183.29 BPM. As with editing **Tempo** directly, the event's timing changes with the pitch. The mod leaves values outside Bitwig's 20–9999 BPM range unapplied.

The build targets Bitwig Studio 6.1.7 on Windows x64 and requires **Framework: Inspector** 0.3.0-dev.2 from Patchbay 0.36.0-dev.14. Import the ZIP using **Mods → Add Mod…**, enable the mod and its framework, and click **Load**.
