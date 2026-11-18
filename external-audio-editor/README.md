# Inspector: External Audio Editor

Status: dev.3 tester build using Bitwig 6.1.4 as Patchbay's reference build.

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

## Updating from dev.2

Import the dev.3 ZIP through Patchbay and accept replacement of the installed mod,
then restart Bitwig. The mod ID and saved editor path stay the same. Dev.2 was
built against Windows 6.1.7, which the supplied macOS 6.1.7 map cannot translate.
Dev.3 ports the Java references to the supported Windows 6.1.4 reference build.

## Compatibility and verification

Built against Bitwig Studio 6.1.4 Windows x64 with Patchbay 0.36.0-dev.16. Requires
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
Dev.3's dialog and launcher classes are byte-identical to dev.2, and its packaged
helper passed the command/source tests including a real Windows process launch.

Dev.3 passed ZIP import, checksums, availability and full offline preparation
with all three frameworks on these builds:

| Target | Result |
| --- | --- |
| Windows 6.1.4 (reference) | `PREPARE_OK` |
| Windows 6.1.7 | All 29 helper references map; `PREPARE_OK` |
| macOS 6.1.7 | All 29 helper references map; `PREPARE_OK` |

Both 6.1.7 checks used official map version 2.0.0-dev.11. Each preparation loaded
4 mods with 27 transforms. The macOS check ran on Windows against the official
macOS Bitwig JARs; it does not validate a live macOS UI or application launch.

Robert confirmed dev.2 working in Bitwig on Windows. Dev.3 keeps that editor and
dialog behavior and changes the Bitwig reference names to 6.1.4. Live testing of
dev.3 in Bitwig remains pending, including the macOS dialog and application launch.
Linux launch commands are implemented but have not been validated live. See the
[repository release guide](https://github.com/polarity/bitwig-mods/blob/main/docs/releasing.md)
for the build and manual acceptance workflow.
