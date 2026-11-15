# Browser: Remember Live Preview

Tester build for Bitwig Studio 6.1.7 on Windows x64. Requires Patchbay 0.36.0-dev.14 and its **Framework: Settings** 1.0.0.

Import the ZIP through **Mods → Add Mod…** in Patchbay, enable the mod and its framework, and click **Load**.

## Settings

In Bitwig, open **Settings → Mods → Pop-up Browser → Live Preview**.

| Mode | Behavior |
| --- | --- |
| Remember last choice | Saves changes to the checkbox globally and restores the choice when opening a popup, including after restarting Bitwig. Uses Bitwig's initial value before the first change. |
| Open with preview on | Starts each popup with Live Preview on. The checkbox still works for the current popup. |
| Open with preview off | Starts each popup with Live Preview off. The checkbox still works for the current popup. |

The dropdown applies to the next popup. The mod sets the native preview value before the popup runs its initial query. It applies to popup contexts with a Live Preview checkbox.

## Tester check

Choose **Open with preview off**. In an empty device chain, open its **+** browser and wait for the first device to be selected. The checkbox should be off, and the selected device should appear in the chain only after confirmation. Repeat in a preset browser for an existing device. Then switch to **Remember last choice**, change the checkbox, and check the next popup and the first popup after a restart.
