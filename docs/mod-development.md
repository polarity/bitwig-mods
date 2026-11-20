# Mod development notes

Document status: Verified recurring fixes from developing the mods in this repository. Each note names where it was observed. Unless stated otherwise, that is Lock States on Bitwig Studio 6.1.4 for Windows.

Purpose: Shorten the path from an idea to a hook that works in Bitwig. The [Patchbay SDK guide](https://github.com/shaboogen-dev/Patchbay/blob/main/docs/sdk/README.md) stays the authority for the mod kit, hooks and frameworks; this page adds only what that guide does not cover. Building and the preflight are in the [release guide](releasing.md).

## Contents

- [Prove that a hook runs before building on it](#prove-that-a-hook-runs-before-building-on-it)
- [Reach method arguments with REPLACE_CALL](#reach-method-arguments-with-replace_call)
- [Read Bitwig's code from the JAR](#read-bitwigs-code-from-the-jar)
- [Inspector widgets the framework lacks](#inspector-widgets-the-framework-lacks)
- [Draw text inside notes](#draw-text-inside-notes)
- [Build numbers and failed preflights](#build-numbers-and-failed-preflights)
- [Keep scratch files out of the mod folder](#keep-scratch-files-out-of-the-mod-folder)

## Prove that a hook runs before building on it

The build and offline preparation check that a hook fits Bitwig's bytecode. They cannot tell whether Bitwig ever executes that code path in the situation you care about. Two Lock States attempts passed every check and did nothing in Bitwig:

- A hook on the note painter's note-name call. The piano roll draws that name only when a flag is set, and it is not set there.
- A hook on a lane-visibility method that was meant to hide automation lanes. The lanes stayed visible; the cause was never established, because the feature was dropped.

Before writing logic for a new hook, ship a build in which the hook only logs its first hit, then perform the action in Bitwig and look for the line:

~~~java
private static final Bml.Mod MOD = Bml.mod("my-mod");
private static final AtomicBoolean SEEN = new AtomicBoolean();

// first statement of the hook
if (SEEN.compareAndSet(false, true)) MOD.log("note-state-label reached");
~~~

`MOD.log` writes to Bitwig's log and the session's mod log ([`bml.api.Bml`](https://github.com/shaboogen-dev/Patchbay/blob/main/docs/sdk/writing.md#bmlapibml)). Log once: painters and engine callbacks run thousands of times. Remove the probe when the hook is confirmed.

A missing line means the path is wrong, not the logic. Read the surrounding bytecode for a condition that guards the call, and pick a call that runs unconditionally.

## Reach method arguments with REPLACE_CALL

The SDK guide documents that a `WRAP_RETURN` hook must not take `@This` or `@Arg`. The same failure occurs with `BEFORE_RETURN` on long methods: Bitwig's code reuses the argument slots before the return, and preparation stops with

~~~text
DEEP_VERIFY_FAIL bro java.lang.VerifyError: Bad local variable type ... locals[1] is not assignable to reference type
~~~

Observed on the note painter `bro.cfM(SYo, Ibv, kPd, DDDDDIJJZZZZZ)V`. The SDK guide does not list this case for `BEFORE_RETURN`.

Use `REPLACE_CALL` hooks on calls inside the method instead. Each hook receives the call's receiver and arguments, which are exactly the values the method holds at that point. When one hook needs values from different points of the method, collect them across several hooks:

1. A hook on an early call stores the object being processed.
2. A hook on a later call stores further values, such as the computed rectangle.
3. A hook on the last suitable call does the work and clears the stored values.

Each hook makes the original call itself and returns its result. [NoteLabels.java](../lock-states/src/com/bitwig/mods/lockstates/NoteLabels.java) and the three `note-state-*` hooks in [LockStates.java](../lock-states/src/com/bitwig/mods/lockstates/LockStates.java) show the pattern. Static fields are enough when the method runs on one thread and does not re-enter, as painting does. Wrap the work in a `try`/`catch` so a failure never reaches Bitwig's code.

## Read Bitwig's code from the JAR

Do not extract `bitwig.jar` on Windows. Obfuscated class names differ only by case (`shO`, `sHo`), and the file system silently overwrites one with the other. Point the tools at the JAR:

~~~powershell
javap -c -p -cp "C:/Program Files/Bitwig Studio/bin/bitwig.jar" bro
~~~

To find classes by string constant, caller or subclass, use Patchbay's `jarsearch.py`, described under [Finding Bitwig names](https://github.com/shaboogen-dev/Patchbay/blob/main/docs/sdk/writing.md#finding-bitwig-names). It searches the JAR in about a second and is the faster start than reading `javap` dumps.

## Inspector widgets the framework lacks

Inspector Framework 0.3.0-dev.2 (`bml.inspector.Rows`) has no button whose label changes, no row with two buttons and no clickable list. [Ui.java](../lock-states/src/com/bitwig/mods/lockstates/Ui.java) builds them from Bitwig's own widgets, which mix freely with framework rows inside `Rows.column`:

| Need | Solution |
| --- | --- |
| Button with a changing label or enabled state | `Ui.button` creates Bitwig's Inspector button and registers an updater. The view runs all updaters in its `refresh()`, before `Rows.refresh(context)`. |
| Two buttons in one row | `Ui.pair` puts both into a horizontal layout group that fills the width. |
| Directly clickable list | A fixed number of button slots, each wrapped in `Rows.when(context, () -> index < count(), …)`. Entries beyond the slots continue in a `Rows.choice`. The active entry carries a `●` prefix, because button colours are not available. |

Both Lock States panels use these: the `View` classes in [LockStates.java](../lock-states/src/com/bitwig/mods/lockstates/LockStates.java) and [NoteInspector.java](../lock-states/src/com/bitwig/mods/lockstates/NoteInspector.java). The widget classes are obfuscated Bitwig names from the 6.1.4 reference build. Other mods copy `Ui.java`; it is not a shared library.

## Draw text inside notes

Piano Roll Framework 0.2.0-dev.1 marks notes with stripes only. Text is drawn with the painter's own helper, which uses Bitwig's font, clips to the note and omits the text when the note is too narrow. [NoteLabels.java](../lock-states/src/com/bitwig/mods/lockstates/NoteLabels.java) calls it from the hooks described [above](#reach-method-arguments-with-replace_call).

The code runs for every painted note. Return after one cheap read for notes without data, and cache anything that needs decoding. Confirmed in the piano roll; the drum editor is unchecked.

## Build numbers and failed preflights

- The builder refuses to overwrite a ZIP with the same version. Raise the version in `mod.json` for every build a tester receives, and update the version mentions in the mod's README in the same step. Replace them by text, not by line number.
- The builder imports and prepares every ZIP offline. When preparation fails, the [package check](../tools/check_package.py) prints the whole preparation log except the passed verifications. The cause, a `PREPARE_FAILED` or `DEEP_VERIFY_FAIL` line, usually stands well above the last lines. The SDK guide lists the [usual causes](https://github.com/shaboogen-dev/Patchbay/blob/main/docs/sdk/building.md#when-something-fails).
- The builder checks only the reference build named in `built_against`. Other Bitwig versions and platforms need their own run, as the [release guide](releasing.md) describes per mod.

## Keep scratch files out of the mod folder

Bytecode dumps, extracted JARs and probe scripts belong in a temporary folder outside the repository. `lock-states/dev` grew to about 40,000 ignored files, and recursive searches and listings over the mod folder then ran into timeouts. Keep in the mod folder only what a later session needs, such as a script that reruns a check.
