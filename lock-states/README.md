# Devices: Lock States

Status: release 0.1.0, with the implementation from dev.16. Robert confirmed that
the current development build works in his Windows setup before requesting this
release. This is user-reported acceptance, not completion of every live check below.
Note assignments, stepped engine-event encoding, flexible recall and native
metadata persistence also have offline tests. Complete project save/reopen,
audio timing and runtime operation on macOS/Linux still need dedicated testing.

## Install

Install **Devices: Lock States** from the `polarity/bitwig-mods` source in Patchbay
Launcher, or import `lock-states-0.1.0.zip` from the
[GitHub release](https://github.com/polarity/bitwig-mods/releases/tag/lock-states-v0.1.0)
through Patchbay's **Mods → Add Mod…**. Enable the mod and its four required
frameworks, then click **Load**. Patchbay 0.36.0-dev.16 supplies the tested
frameworks and maps. The same ZIP serves Windows, macOS and Linux.

Lock States keeps named parameter snapshots for each top-level native device and
its nested tree. Lists and the selected state are embedded in project data.
There is no Library, bank picker or New Bank step. Recall switches device-wide
parameter values, including those affecting sounding voices.

## Use and state ownership

1. Select a native instrument or audio effect and open its Device Inspector.
2. The outermost device on the track owns the list. A selected nested effect
   shows this same parent list, marked **States of** and the parent's name.
3. Set the controls and click **New**. The first capture creates the list.
4. Click a state in the list to recall it. The active state is marked with ●.
   The list shows 16 states; further ones appear in a **More** dropdown.
5. **Update** replaces only the active snapshot with the current tree/values.
   **Rename…** and **Delete…** edit that list. Save the project normally.

| Selection / placement | State owner |
| --- | --- |
| Polymer on a track | Polymer, including its internal chains |
| Reverb inside Polymer, or Delay inside that Reverb | The outermost Polymer |
| Delay placed after Polymer on the track | Delay, with its own list |
| Filter nested inside that standalone Delay | The outermost Delay |
| Instrument inside a top-level Instrument Layer | Instrument Layer, including its instrument branches |

This rule applies equally to instruments, audio effects and nested containers.
Adjacent devices on a track are separate owners. Nested devices do not create
competing local lists. Earlier local lists from dev.4 are retained on their
original devices, not merged or erased. Moving such a device out to the track
makes its old local list accessible again.

Clicking a state recalls it at once; unsaved control changes are replaced and
return with Undo. Editing controls does not overwrite a saved state. Shortcut
navigation stops at either end. Opening a project restores the list and
selection without recalling over the project's saved sound.

Commander/shortcut actions: **Recall Lock State**, **Previous Lock State** and
**Next Lock State**. They resolve the selected device's parent in the invoking
project window, without requiring the Inspector to be open first.

## Assign states to sequencer notes

1. Capture the desired states in the parent device's Device Inspector.
2. Select one or more notes in a single clip and open the Note Inspector.
3. In **Lock States**, click a state to assign it to the selected notes. The
   assigned state is marked with ●. Assignment enables **Follow Notes**.
4. Assign other notes as needed. Save the project to retain the assignments.
5. Click **None** to unbind the notes. With several state owners on the track,
   a **Device** dropdown chooses the owner and **Clear All Devices** clears
   every owner on the selected notes.

In the note editor an assigned note shows its state number inside the note.
Notes addressing several owners show one number per owner, as in `2/1`, and
`?` marks a deleted state. The number is omitted on notes too narrow for it.

Bindings live on individual native notes, rather than MIDI pitches. Moving or
copying a note carries its binding. One note can address several top-level device
owners on its track. A binding addresses a saved state by ID: updating the state
changes its sequence values; deleting it makes the binding inactive.

Changes occur at the assigned note's stored beat position and hold until the next
applicable state. Unassigned notes do not reset the controls. Before the first
assignment in a clip, each managed parameter uses its baseline from first binding.
Simultaneous notes for one parent must use the same state; conflicting states are
refused. Changes are device-wide and also affect overlapping voices and effect
tails. They are not per-voice snapshots.

The mod compiles HOLD events through Bitwig's native automation encoder, using
managed native automation clips linked to the note clip's timing. It does not
write knobs or create Undo steps on a UI playback timer. Managed persistent lanes
contain clips with one recognizable baseline point; note-state points are generated for the
engine, rather than materialized as editable automation. Ordinary automation is
never replaced: existing arrangement/clip automation on an affected parameter
blocks assignment. Editing a managed baseline or adding points preserves the
manual data and suspends generated playback for the affected parent/clip.
The per-parameter lanes are ordinary native project data and stay visible in
Bitwig's automation display, where each shows its baseline placeholder. Since dev.13
the mod no longer filters them from view. Reassigning
another note in the same clip reuses its existing managed lanes and clips.
Lane creation does not select lanes or expand the track. Bitwig subsequently adds
HOLD repair points before, between and after arrangement clips. Only points at
these native boundary positions with the matching saved baseline are accepted;
other points or changed values/interpolation still block assignment. Existing
dev.9 lanes can therefore be reused without deleting their data. A failed
assignment removes newly created clips/lanes.

**Follow Notes** in the Device Inspector switches generated playback on and off.
A manual recall, from the list or a shortcut, switches it off first; click
**Follow Notes** again to resume the sequence. Bindings
and the playback switch are project data and participate in native Undo/Redo.
Do not record or edit automation on managed controls while using note playback.

Current limits:

- Enabled Chance below 100%, Occurrence other than Always and Recurrence with
  skipped iterations are refused. Neutral operators and disabled operators with
  retained non-neutral values are accepted. Bitwig enables Occurrence/Recurrence
  switches on ordinary notes by default; the switches alone are not conditions.
  The sequence cannot follow conditional audio-engine Note On decisions.
- Timing uses stored note positions. Groove/shuffle and other transformations
  of the audible onset have not been validated; use straight timing for testing.
- Capturing/updating a state with newly added parameters requires assigning it
  again to create their managed lanes. Missing lanes suspend that clip's program.
  Newly added effects absent from old snapshots remain unchanged; removed targets
  are skipped as in manual recall.
- Shared automation content must generate identical programs in all its clips.
  Conflicting shared clips and ambiguous copied parent identities suspend playback;
  make the clips independent or bind the intended parent again.
- A clip supports at most 16,384 bound events and 262,144 parameter changes per
  parent. Dense full-device sequences can be expensive to rebuild when edited.
- Piano-roll audition and live MIDI input do not trigger these bindings. Launcher,
  arrangement, loops, seeks, clip edits and export still require live acceptance.

## Flexible recall and chain changes

Each new snapshot has its own parameter descriptors and device/module layout.
Existing states are not expanded when another state captures a new effect.

- Surviving devices restore compatible captured parameters, even after insertion,
  removal or reordering elsewhere in the tree.
- Newly added devices and parameters stay unchanged when recalling older states.
- Removed devices and missing/incompatible parameters are skipped. Their values
  remain in the old snapshot; restoring the device with its identity allows
  recall again. An unrelated replacement of the same type is a new device.
- The status line reports parameters applied, skipped and left unchanged. No
  compatible saved parameters is an explicit refusal, not a successful recall.
- Capture stores the current supported tree. Update replaces only that state's
  scope. Old states keep their original scope and values.

Device and modulator identities are stored in a native document property. Matching
uses these owner-relative tokens, not list indices, device names or device type
alone. Copies of an entire owner retain relative identities in their independent
list. A child duplicated inside one owner gets a new token; the original keeps
its identity. Native UUIDs distinguish source and copy. Ambiguous duplicates are
refused before writes; undo the child copy, Capture/Recall once, then duplicate
again. Internal modules retain their role and type so a replaced module cannot
accidentally receive an old module's parameter values.

Recall validates all matched targets before editing. Automation on a matched
parameter, non-writable matched controls and transport automation-write mode
block the operation. Automation/read-only controls on an uncaptured new effect
do not block it. Recall creates no automation points. Parameters and selected
state share one Undo transaction; failed writes/metadata commits roll matched
values and new identity assignments back. New effects are not written by recall.

## Upgrading older states

Dev.4 stored positional paths without stable child identities. Dev.5 can migrate
these states only while the original complete tree still matches. Before adding,
removing or reordering devices, **Recall**, **Capture** or **Update** once in
dev.5 and save the project. All old states in that list acquire identity paths
without changing their names, IDs or values.

If the chain has already changed, restore its original structure for this one-time
upgrade. The mod refuses to guess which identical device an old position meant.
A failed migration leaves all old data intact. This restriction applies to
positional legacy states; new and successfully migrated states use flexible recall.

Dev.1-dev.3 JSON banks in Patchbay's mods/lock-states/banks directory remain
preserved as backups. Their device association was never stored in projects, so
the mod cannot automatically assign them to an owner.

## Snapshot scope and project storage

Snapshots contain native base parameter values: continuous controls, switches and
indexed choices, including native modulator controls. Modulation assignments and
depths, Grid wiring, sample/wavetable resources and non-parameter curves remain
unchanged. Changing these can change the sound independently of a state.

Capture/Update refuse plug-ins, unknown parameter representations, missing direct
parameters and oversized supported trees rather than silently omitting controls.
An uncaptured plug-in added later is skipped during recall along with its subtree.
Plug-in state chunks, morphing and independent states for overlapping voices are
not implemented. Note bindings sequence these supported base parameter values.

The native serialized properties polarity_lock_states,
polarity_lock_states_identity, polarity_lock_states_note and
polarity_lock_states_curve carry lists, identities, note assignments and generated
curve metadata. Bitwig's document
serialization, cloning and Undo machinery handle them. No external file,
project path or global bank lookup is required. Save As and moving a project
therefore require no reassignment. Separate top-level instances/projects remain
independent; copying an owner copies a separately editable list.

Corrupt/future state data is reported and retained. Limits: 256 states, 16,384
parameters per snapshot and a 32 Mi-character serialized payload per owner.
Capture, state selection and edits mark the project changed. The Inspector follows
native Undo/Redo notifications.

Keep the mod enabled when opening and saving these projects. Stock Bitwig cannot
retain an unregistered custom field when resaving. This is a development extension,
not an official Bitwig project API.

## Compatibility and validation

Reference: Windows Bitwig Studio 6.1.4, Java release 21. Required frameworks:
Inspector 0.3.0-dev.2, Actions and Menus 0.8.0-dev.1, Notifications 0.3.0-dev.1,
and Piano Roll 0.2.0-dev.1.
Other versions/platforms require Patchbay's maps for that reference.

Mandatory preflight runs against the ZIP's actual helper:

- 258 core assertions: capture/recall, strict JSON, rollback, individual snapshot
  scopes, added/removed/reordered/replaced effects, untouched automation/read-only
  targets, stable module paths, duplicate identity handling, safe legacy migration,
  note/curve metadata, ordering, chord conflicts, baselines and compatible sequences.
- 51 native range assertions: real Boolean/indexed/integer controllers, descending/
  sparse choices (including Octave's 0, -1, -2), full signed-32-bit ranges and exact readback.
- 180 native document/sequence assertions: real loader transformations, serialized list and
  identity properties, observers, string Undo/Redo deltas, binary roundtrip in a
  second process, cloning, independent edits, parent ownership links and identity
  rollback, note binding Undo/Redo and cloning, native HOLD encoding, exact indexed/
  full-width integer points, baseline sentinels and detection of manual edits.
  Ordinary default notes, all native occurrence modes, neutral/conditional recurrence
  patterns at periods 2..8, enabled/disabled Chance, clone and serialization exercise
  the actual assignment condition guard in the packaged helper.
  Boolean switches with both initial defaults test engine events, held values,
  baseline recognition, cloning and native serialization. This canary reproduces
  dev.7's Unsupported native automation range failure against its packaged helper.
  A native track integration canary reproduces the deferred-lane insertion error,
  then exercises the production persistent-lane factory, arrangement clip insertion,
  note metadata, source lookup, geometry copying, lane reuse, track serialization
  and cleanup. This catches dev.8's about-to-be-created-lane failure.
  The fixture now runs Bitwig's real end-event repair, assigns different states to
  two native notes, verifies lane reuse and stepped values, and reopens the native
  track. It checks unchanged repair points, managed-lane recognition and protection of
  added/edited manual points. The private native lifecycle reference is compile-
  checked and named through modkit access metadata for test-only reflection.
  These use bare native documents and an isolated core schema registry. One editor-
  mode flag is supplied by a strict test proxy; other application service calls fail.
  They do not initialize instruments, complete projects, Undo history, Nitro resources
  or an audio engine. Launcher insertion still needs an initialized project/live test.

A failed preflight prevents READY and the final ZIP. Tests are not shipped in the
mod. The [release workflow](../docs/releasing.md#lock-states-prototype) describes
the commands. Offline preparation does not establish live UI/audio behavior.

Release 0.1.0 offline matrix (2026-11-20):

| Target JAR / map | Import/checksums/availability | Preparation | Live acceptance |
| --- | --- | --- | --- |
| Windows 6.1.4 | Pass | PREPARE_OK | Pending |
| Windows 6.1.7 | Pass | PREPARE_OK | dev.16 accepted by Robert; full checklist open |
| macOS 6.1.4 | Pass | PREPARE_OK | Pending |
| macOS 6.1.7 | Pass | PREPARE_OK | Pending |
| Linux 6.1.4 | Pass | PREPARE_OK | Pending |
| Linux 6.1.7 | Pass | PREPARE_OK | Pending |

Offline checks run on Windows against official target JARs and Patchbay
0.36.0-dev.16 maps. They do not establish execution on macOS or Linux.

Detailed live acceptance checklist (not yet exhaustively completed):

- Assign alternating states to individual notes and same-state chords. Verify
  sound at note attacks, held values, unassigned notes, seeking, loops, launcher,
  arrangement and export. Check timing at fast tempos and small audio buffers.
- Move, duplicate, resize, split and consolidate notes/clips. Test shared clips,
  muted notes, chance/occurrence refusal and simultaneous-state conflict refusal.
  Save, close and reopen; compare assignments, generated timing and playback mode.
- Undo/Redo assignment, removal and playback toggles. Disable playback, recall
  manually, enable again and verify automation control resumes. Check manual
  automation conflicts and suspension after editing a managed baseline.
- Verify that managed lanes appear in the native automation views with their
  baseline placeholder, including in an already open dev.9 project.
- Migrate a dev.4 project before editing the chain; save, close and reopen it.
  Verify lists, order, selection, exact recalled values and saved sound on opening.
- Add an effect before two same-type captured effects; remove/reorder them and
  recall old states. Verify survivors, untouched additions and unrelated same-type
  replacements. Capture/Update the new tree and compare old/new state scopes.
- Duplicate the entire owner and a child inside it. Check independent editing,
  identity retention/reassignment and project save/reopen after both operations.
- Select nested devices at multiple depths inside an instrument and an audio
  effect; verify State owner, shared list, shortcuts and sibling track isolation.
- Undo/Redo capture, migration, selection, edit, delete and recall. Check dirty
  indicator, Inspector refresh and restored device identities after device Undo.
- Compare at least 20 native continuous/discrete/modulator controls and sound in
  Polymer, Polymer with internal Reverb and an Instrument Layer. Check unsupported
  controls, hidden controls, automation refusals and multiple project windows.
- Repeat on the supported OS/build matrix before claiming runtime support.

## Native range regressions

Dev.3 fixed descending Octave choices: format-1 minimum/maximum fields are raw
values at normalized zero/one, not sorted bounds. Never sort them or omit the
parameter. Dev.2 separately fixed native integer overflow using declared bounds
and 64-bit cardinalities. Both regressions remain mandatory in 0.1.0, including
the generated native automation points.
