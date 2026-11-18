# Build and verify release ZIPs

## Release workflow

1. [Build the mod ZIP locally](#local-build), verify Patchbay import and offline preparation, and give the ZIP to Robert.
2. Robert installs the ZIP through Patchbay and tests the mod in Bitwig. If a fix is needed, rebuild and send the updated ZIP for another test.
3. After Robert confirms the build in Bitwig, check the
   [shared Patchbay Launcher manifest](patchbay-launcher.md#mods) and publish that
   ZIP as an asset on the GitHub release.
4. Draft a short English Discord announcement that explains what the mod does and links to the GitHub release.

## Local build

Build the tester ZIPs on Windows x64 with Python 3, JDK 25.0.4.1, and the extracted [Patchbay 0.36.0-dev.14 Windows bundle](https://github.com/shaboogen-dev/Patchbay/releases/tag/0.36.0-dev.14). Check out the [matching Patchbay source tag](https://github.com/shaboogen-dev/Patchbay/tree/0.36.0-dev.14) for the SDK builder.

From the repository root, run the command with your own paths. The default builds Scale Fold and Step Sequencer. Point `--bitwig-bin` at stock Bitwig Studio 6.1.3 for these two mods.

~~~powershell
python tools/build_releases.py --patchbay-source C:/work/Patchbay --patchbay-release C:/work/BML-0.36.0-dev.14-windows --bitwig-bin "C:/Program Files/Bitwig Studio/bin" --jdk-bin C:/work/jdk-25.0.4.1/bin
~~~

To build Repitch Transpose, pass `--mod repitch-transpose` and point `--bitwig-bin` at stock Bitwig Studio 6.1.7. The bundle must contain Inspector Framework 0.3.0-dev.2.

To build Remember Live Preview, pass `--mod popup-live-preview` and use stock Bitwig Studio 6.1.4 with Patchbay 0.36.0-dev.16 and Settings Framework 1.0.0. Keep its 6.1.4 reference build so the supplied maps can translate it to Windows and macOS 6.1.7. Verify both runtime builds when changing Bitwig references.

To build External Audio Editor, pass `--mod external-audio-editor` and use stock
Bitwig Studio 6.1.4 with Patchbay 0.36.0-dev.16. Keep its 6.1.4 reference build
so the supplied maps can translate it to Windows and macOS 6.1.7. Verify package
availability and offline preparation on both targets after changing Bitwig
references. Its build file pins Inspector, Settings and Notifications.
Run the standalone command/source tests with JDK 21+
by compiling `EditorCommand.java`, `SourceFiles.java` and `test/EditorCommandTest.java`
from the mod directory into a temporary class directory, then run
`com.bitwig.mods.externalaudio.EditorCommandTest`. On Windows, compile
`test/EditorProbe.cs` as an executable with the .NET C# compiler and pass its full
path to the test to also verify a real process receives an unchanged sample path.

For External Audio Editor dialog changes, also compile `DialogProcess.java` and
`test/DialogProcessTest.java` into a temporary class directory. Extract the built
`helper.jar` from the ZIP. Run `com.bitwig.mods.externalaudio.DialogProcessTest`
with Bitwig's bundled Java, `-Djava.awt.headless=true`, and arguments `<helper.jar>
<existing-editor-executable>`. Click **Save** in the child dialog and expect
`DIALOG_PROCESS_TEST_OK save parent=headless`. Repeat with `cancel` as the third
argument and close the dialog. It must report `cancel parent=headless`. Bitwig's
headless process must never create an AWT/Swing window itself.

To build Note Intervals or Chord Tools, pass `--mod note-intervals` or `--mod chord-tools`, respectively. Use stock Bitwig Studio 6.1.4 and the [Patchbay 0.36.0-dev.16 Windows bundle](https://github.com/shaboogen-dev/Patchbay/releases/tag/0.36.0-dev.16). Keep the 6.1.4 reference build so the supplied maps can translate both mods to Windows and macOS 6.1.7. Verify package availability, helper references, and offline preparation for both targets after changing Bitwig references. Preserve action IDs when porting so assigned shortcuts carry over. Each mod declares its framework versions in its build file. [Note Intervals](../note-intervals/build.json) · [Chord Tools](../chord-tools/build.json)

The builder generates one versioned ZIP per mod in `dist/`. Use `--out` to choose a fresh folder for a repeat build. Each ZIP includes the compiled helper, generated manifest, sources and checksums. Use **Mods → Add Mod…** in Patchbay to import the ZIP.

### Step Sequencer and Scale Fold preflight

The release builder checks the compiled helpers for Bitwig's generic widget child-removal call. The old Step Sequencer hook used that call to move the Drum/Hybrid button and could leave it without a parent during layout. A matching call reports `PREFLIGHT_FAILED` with the method reference and stops the build before the ZIPs reach their final paths.

The check uses the method name and descriptor for the Bitwig build named in `built_against`. If that reference changes, verify the corresponding native method before adding its signature in [the build script](../tools/build_releases.py). The check covers this removal call. Patchbay offline preparation and a live Detail Editor test remain part of the release workflow.

## Installing or replacing tester builds

Leave the individual release packages zipped for import. GitHub's automatically generated **Source code** archive does not include the compiled helper JARs. If Patchbay offers to replace an existing copy, accept. If you installed a source folder under a different name, move it out of Patchbay's `mods` directory first.

The Scale Fold and Step Sequencer ZIPs passed import and offline preparation together on Bitwig Studio 6.1.3 Windows x64.
