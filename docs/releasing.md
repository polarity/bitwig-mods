# Build and verify release ZIPs

## Release workflow

1. [Build the mod ZIP locally](#local-build) and give the ZIP to Robert. The builder verifies Patchbay import and offline preparation for the reference build.
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

After building, the builder imports the new ZIPs with the bundle's released loader into an isolated folder, verifies their checksums and availability, and runs Patchbay's offline preparation together with the frameworks each `build.json` pins. A failure prints the preparation log without the passed verifications, reports `PREFLIGHT_FAILED` and stops before any ZIP reaches its final path. Preparation needs Bitwig's `jre` folder next to `bin`; for a bare folder of reference JARs, pass `--verification-runtime <Bitwig jre folder>`. Only the reference build named in `built_against` is prepared. To check a finished ZIP against another Bitwig version or platform, run [check_package.py](../tools/check_package.py) with `--package` (repeatable), `--patchbay-release`, `--jdk-bin`, `--bitwig-bin`, the matching `--map-directory` and a fresh `--out`.

The builder generates one versioned ZIP per mod in `dist/`. Use `--out` to choose a fresh folder for a repeat build. Each ZIP includes the compiled helper, generated manifest, sources and checksums. Use **Mods → Add Mod…** in Patchbay to import the ZIP.

### Lock States prototype

The first published version is 0.1.0, retaining dev.16's implementation after
Robert's acceptance. The heading remains stable for existing links. Current
release scope and the platform validation matrix live in
[Lock States](../lock-states/README.md#compatibility-and-validation).

Use `--mod lock-states` with stock Windows Bitwig 6.1.4, JDK 25.0.4.1 and Patchbay
0.36.0-dev.16. Its build pins Inspector, Actions and Menus, Notifications and Piano Roll.
The release builder automatically runs a mandatory preflight on the newly built
ZIP's actual helper JAR: 258 core assertions and 51
headless canaries using the real Windows 6.1.4 native range/controller classes.
These cover native Boolean switches, descending/sparse choices including Octave's
`0, -1, -2`, full 32-bit integer ranges and exact discrete readback. The SDK compile JAR is reused
to compile the typed native tests; the mod kit translates their references.
It also imports and transforms that ZIP with the released loader and runs 180
native document/sequence assertions: per-instance storage, observers, native Undo/
Redo deltas, binary serialization, cloning and restoration in a second process;
note binding metadata, native conditional-note fields, HOLD encoding, descending
indexed/full-width integer points and baseline sentinel/manual-edit detection.
Dev.5 additionally tests flexible matching, per-state layouts, same-type device
replacement, child/owner copies, parent ownership, serialized identities and
identity rollback. Legacy positional snapshots must migrate against their
unchanged original tree before chain edits; the tests verify refusal to guess.
Dev.6 tests individual note assignment codecs, chord conflict refusal, event
ordering, baseline behavior, compatible surviving parameters and playback flags.
Native document canaries compile against a signatures-only copy of the prepared
helper with SDK aliases restored; execution uses the actual prepared helper.
Dev.7 exercises the actual native note-condition guard with ordinary default notes,
all occurrence modes, recurrence periods 2..8 and Chance. Enabled switches with
neutral values and disabled operators with retained values must accept assignment;
effective conditional operators must refuse with an operator-specific message.
Dev.8 adds native Boolean points to the automation encoder; both initial switch
defaults test Off/On events, held values and serialized/cloned baseline sentinels.
The added canary fails against the actual dev.7 helper with Unsupported native
automation range, reproducing the assignment failure before any project edits.
Dev.9 tests the production lane/arrangement-clip insertion path on a native track,
including source-note metadata, clip timing, reuse, serialization and cleanup.
It reproduces the old deferred-placeholder error, then verifies persistent lanes
created without automatic editor selection. The bare
track fixture supplies one editor-mode flag through a strict test proxy. Complete
UI/Undo/audio operation and launcher insertion remain live acceptance checks.
Dev.10 additionally invokes Bitwig's actual end-event arrangement repair via
test-only reflection; the method name comes from the typed reference's modkit
access metadata. It verifies the resulting HOLD boundary points, sequential note
assignment/lane reuse, two-state native envelopes, track roundtrip, manual-point
protection and managed-lane recognition. Since dev.13 the lanes are no longer hidden.
The document probe registers the real core schemas in isolation; it needs no
Nitro resources. The probes open no application, complete project or audio engine
and are not shipped. A live project save/reopen remains a separate acceptance check.
Any failure reports `PREFLIGHT_FAILED` and stops before the ZIP reaches its final
output path or `READY` is printed. Passing reports the tested helper's SHA-256.

Run `tools/test_lock_states.py --jdk-bin <JDK/bin> --bitwig-bin <stock-6.1.4-bin>`
to test core source during development. Add `--package <mod.zip>` to exercise a
compiled package; also add `--modkit-jar <BML/modkit/bml-modkit.jar>` to run the
native reference canaries; this also requires `--patchbay-release <BML>` for the
released loader/frameworks and reference map. An optional `--compile-jar` reuses an existing SDK
compile JAR. Without it the test runner creates one in its temporary directory.

Run `tools/check_lock_states.py`
with `--package`, `--patchbay-release`, `--jdk-bin`, `--bitwig-bin`,
`--map-directory` and a fresh `--out` to test the released importer, checksums,
availability and offline preparation without touching the user's installation.
It uses the shared [package check](../tools/check_package.py) and adds the Lock States tests.
This package checker also runs the 258 packaged-core assertions before import.
For Windows 6.1.4, add `--modkit-jar` (and optionally `--compile-jar`) to repeat
the native document/process-restart canaries after preparation.
For target-platform JARs on another host OS, also pass `--verification-runtime`
pointing to that host's Bitwig JRE; the checker stages an isolated verification
installation. This verifies Java compatibility, not execution on the target OS.

Negative validation: the earlier range preflight reproduced dev.2's real Octave
descriptor failure against its packaged helper. The native document gate also
rejects a package without the schema-registration hook. Current regressions cover
tested matching, range and persistence semantics, not arbitrary UI/engine failures.

Publish only after Robert accepts the current development build. Record that
acceptance separately from the detailed live checklist, which is not fully
completed for 0.1.0. Note binding uses generated native automation. Audible timing,
complete clip workflows and project reopening require live tests; offline
preparation does not validate them. Morphing remains unimplemented.

### Step Sequencer and Scale Fold preflight

The release builder checks the compiled helpers for Bitwig's generic widget child-removal call. The old Step Sequencer hook used that call to move the Drum/Hybrid button and could leave it without a parent during layout. A matching call reports `PREFLIGHT_FAILED` with the method reference and stops the build before the ZIPs reach their final paths.

The check uses the method name and descriptor for the Bitwig build named in `built_against`. If that reference changes, verify the corresponding native method before adding its signature in [the build script](../tools/build_releases.py). The check covers this removal call. Patchbay offline preparation and a live Detail Editor test remain part of the release workflow.

## Installing or replacing tester builds

Leave the individual release packages zipped for import. GitHub's automatically generated **Source code** archive does not include the compiled helper JARs. If Patchbay offers to replace an existing copy, accept. If you installed a source folder under a different name, move it out of Patchbay's `mods` directory first.

The Scale Fold and Step Sequencer ZIPs passed import and offline preparation together on Bitwig Studio 6.1.3 Windows x64.
