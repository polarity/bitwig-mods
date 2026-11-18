# Browser: Remember Live Preview

Tester build using Bitwig Studio 6.1.4 on Windows x64 as Patchbay's reference build. Requires Patchbay 0.36.0-dev.16 and its **Framework: Settings** 1.0.0. Patchbay translates this build for Bitwig 6.1.7 on Windows and macOS using its supplied maps.

Import the ZIP through **Mods → Add Mod…** in Patchbay, enable the mod and its framework, and click **Load**.

## Settings

In Bitwig, open **Settings → Mods → Pop-up Browser → Live Preview**.

| Mode | Behavior |
| --- | --- |
| Remember last choice | Saves changes to the checkbox globally and restores the choice when opening a popup, including after restarting Bitwig. Uses Bitwig's initial value before the first change. |
| Open with preview on | Starts each popup with Live Preview on. The checkbox still works for the current popup. |
| Open with preview off | Starts each popup with Live Preview off. The checkbox still works for the current popup. |

The dropdown applies to the next popup. The mod sets the native preview value before the popup runs its initial query. It applies to popup contexts with a Live Preview checkbox.

## Updating from dev.1

Import the dev.2 ZIP and accept replacement of the installed mod. Its ID and saved settings stay the same. Dev.1 was built against Windows 6.1.7, which the supplied macOS 6.1.7 map cannot translate. Dev.2 uses the supported 6.1.4 reference build.

## Validation

Dev.2 passed Patchbay import, checksums, availability and offline preparation for Windows 6.1.4, Windows 6.1.7 and macOS 6.1.7. Tests using the prepared 6.1.7 classes for both platforms verified the three modes, preview off before the first native query, saved state across Java process restarts, and listener cleanup. These checks ran on Windows against the official platform JARs. Live Bitwig testing of dev.2 on Windows and macOS remains pending.

## Tester check

Choose **Open with preview off**. In an empty device chain, open its **+** browser and wait for the first device to be selected. The checkbox should be off, and the selected device should appear in the chain only after confirmation. Repeat in a preset browser for an existing device. Then switch to **Remember last choice**, change the checkbox, and check the next popup and the first popup after a restart.
