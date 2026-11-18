# Inspector: External Audio Editor

Status: prerelease, confirmed working by Robert in Bitwig on Windows.

Opens the original sample used by one selected audio clip or audio event in an
external editor. Bitwig handles reloading changes saved to that
file; the mod only starts the editor.

## Use

1. Import the mod ZIP in Patchbay with **Mods → Add Mod…**, enable it and its
   required frameworks, then click **Load**.
2. In Bitwig, open **Settings → Mods → External Audio Editor**. Set **Application**
   to **Configure editor...** to open the path dialog. Enter the full application
   path without quotes or command-line options, or use **Browse...**, then **Save**.
3. Select one audio clip in the Arranger or Launcher, or one audio event in the
   Detail Editor. Click **Edit externally** in its Inspector.
4. Save in the external editor to the same source file for Bitwig to pick up the
   changes. The editor path persists across restarts.

The first **Edit externally** click also offers configuration when no editor has
been saved. Cancel leaves the setting and sample unchanged.

| Platform | Application path | Invocation |
| --- | --- | --- |
| Windows | Full path to an editor `.exe` | Executable followed by the absolute sample path as one argument. |
| macOS | Full path to an editor `.app`, or an executable | `/usr/bin/open -a /Applications/Editor.app /path/to/sample.wav` for app bundles; executables receive the sample path directly. |
| Linux | Full path to an executable or executable wrapper script | Executable followed by the absolute sample path as one argument. |

Paths containing spaces or non-ASCII characters require no extra quoting. The mod
does not run a shell. Editors requiring additional options can use an executable
wrapper script on macOS/Linux. Windows batch files, shell command strings,
`.desktop` launchers and extra arguments are not supported.

## Selection and file behavior

- The whole original file opens, including audio outside the clip/event boundaries.
  The mod does not render, trim, duplicate, replace or convert audio.
- Several slices or regions referencing the same file resolve to that one file.
  A clip using different files asks you to select an individual event instead.
- Multiple selected clips/events and MIDI notes have no editor button. Missing or
  unresolved files and editor launch errors show a notification.
- Audio formats are not restricted to WAV: the configured editor must support the
  actual source format. Saving a new filename does not relink the Bitwig event.
- Editing a source affects every clip that uses that file. Make a separate copy
  first if an independent edit is needed. External edits are outside Bitwig undo.

## Compatibility and verification

Built against Bitwig Studio 6.1.7 Windows x64 with Patchbay 0.36.0-dev.16. Requires
**Framework: Inspector** 0.3.0-dev.2, **Framework: Settings** 1.0.0 and
**Framework: Notifications** 0.3.0-dev.1. Settings 1.0.0 has no text/path control,
so its configuration dropdown opens a separate, non-modal Java desktop dialog.
The dialog uses Bitwig's bundled Java in its own process, because Bitwig itself
runs Java headless. No additional Java installation is needed.

The standalone tests passed for Windows/Linux argument vectors, macOS app bundles,
invalid paths and ambiguous sources. A real Windows test executable received the
sample path unchanged, including spaces, Unicode and shell metacharacters.
In dev.2, the packaged dialog opened using Bitwig's bundled Java from a headless
parent process; saving returned the selected path, and closing returned no path.
Patchbay 0.36.0-dev.16 imported the ZIP and returned `PREPARE_OK` on stock Bitwig
6.1.7 Windows x64 with all three frameworks (4 mods, 27 transforms).

Robert confirmed dev.2 working in Bitwig on Windows. Cross-platform launch
commands are implemented; macOS and Linux still need live validation. See the
[repository release guide](https://github.com/polarity/bitwig-mods/blob/main/docs/releasing.md)
for the build and manual acceptance workflow.
