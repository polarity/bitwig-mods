# Polarity's Bitwig Mods

## Getting started

- **Use the launcher.** On Windows and macOS, install Patchbay in the
  unofficial Patchbay Launcher, then open **Mods** or **Devices** to pick
  what to add. The launcher handles updates for Patchbay, mods, and devices.
  [Download Patchbay Launcher](https://github.com/arius77R/patchbay-launcher/releases)
- **Install manually.** Patchbay Mod Loader patches Bitwig at startup and
  includes the frameworks that mods and devices use. Pick a release that
  supports your Bitwig version and follow `INSTALL.md` in the ZIP.
  [Download Patchbay](https://github.com/shaboogen-dev/Patchbay/releases)

  - Get mod ZIPs from [Patchbay Mods](https://github.com/shaboogen-dev/Mods/releases).
  - Get Polarity's mod ZIPs from [Bitwig Mods](https://github.com/polarity/bitwig-mods/releases).
  - Get Polarity's devices and modulators from [Bitwig Devices](https://github.com/polarity/bitwig-devices#install).

## What's in this repo

My mods add tools and shortcuts to Bitwig Studio's piano roll, inspector, and
pop-up browser.

| Mod | What it adds |
| --- | --- |
| [Piano Roll: Scale Fold](scale-fold/README.md) | A compact note grid that shows every pitch in the current scale. |
| [Piano Roll: Step Sequencer](step-sequencer/README.md) | A sixteenth-note grid for drawing steps and editing note parameters. |
| [Piano Roll: Note Intervals](note-intervals/README.md) | Assign shortcuts to transpose or duplicate selected notes at intervals. |
| [Piano Roll: Chord Tools](chord-tools/README.md) | Assign shortcuts for chord inversions, voicings, and chord quality. |
| [Inspector: Repitch Transpose](repitch-transpose/README.md) | Semitone controls for a single Repitch audio event in the Inspector. |
| [Browser: Remember Live Preview](popup-live-preview/README.md) | A saved Live Preview choice or a fixed starting state for the Pop-up Browser. |

## Install

For manual setup, grab the individual mod ZIPs from [Releases](https://github.com/polarity/bitwig-mods/releases). In Patchbay, use **Mods → Add Mod…**, pick the ZIPs, enable the mods, and click **Load**. Step Sequencer also needs **Framework: Piano Roll**.

The piano roll tester builds passed import and offline preparation with [Patchbay 0.36.0-dev.14](https://github.com/shaboogen-dev/Patchbay/releases/tag/0.36.0-dev.14) and Bitwig Studio 6.1.3 on Windows x64. Repitch Transpose targets Bitwig Studio 6.1.7 on Windows x64 and needs **Framework: Inspector**. See the [release guide](docs/releasing.md) for build and upgrade details.

## Publishing and forks

Follow the [shared Patchbay Launcher manifest](docs/patchbay-launcher.md#mods)
for ZIP names, release assets, and adding a fork as a source.

## Devices

My native devices and modulators live in [bitwig-devices](https://github.com/polarity/bitwig-devices). Patchbay loads them through **Framework: Nitro Devices** (`custom-device-registrar`). To make your own, start with [Bitwig Device Blueprints](https://github.com/polarity/bitwig-devices-blueprint).

The mods were originally contributed to [shaboogen-dev/Mods](https://github.com/shaboogen-dev/Mods/commit/8a773ee3537fe9a873584f8c27f054bac21ca4f0). See [LICENSE](LICENSE).
