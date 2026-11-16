# Polarity's Bitwig Mods

My mods for Bitwig Studio. Mod authors keep their mods in their own repos, and [Patchbay Mod Loader](https://github.com/shaboogen-dev/Patchbay) loads them when Bitwig starts.

| Mod | What it adds |
| --- | --- |
| [Piano Roll: Scale Fold](scale-fold/README.md) | A compact note grid that shows every pitch in the current scale. |
| [Piano Roll: Step Sequencer](step-sequencer/README.md) | A sixteenth-note grid for drawing steps and editing note parameters. |
| [Piano Roll: Note Intervals](note-intervals/README.md) | Assign shortcuts to transpose or duplicate selected notes at intervals. |
| [Piano Roll: Chord Tools](chord-tools/README.md) | Assign shortcuts for chord inversions, voicings, and chord quality. |
| [Inspector: Repitch Transpose](repitch-transpose/README.md) | Semitone controls for a single Repitch audio event in the Inspector. |
| [Browser: Remember Live Preview](popup-live-preview/README.md) | A saved Live Preview choice or a fixed starting state for the Pop-up Browser. |

## Install

Grab the individual mod ZIPs from [Releases](https://github.com/polarity/bitwig-mods/releases). In Patchbay, use **Mods → Add Mod…**, pick the ZIPs, enable the mods, and click **Load**. Step Sequencer also needs **Framework: Piano Roll**.

The piano roll tester builds passed import and offline preparation with [Patchbay 0.36.0-dev.14](https://github.com/shaboogen-dev/Patchbay/releases/tag/0.36.0-dev.14) and Bitwig Studio 6.1.3 on Windows x64. Repitch Transpose targets Bitwig Studio 6.1.7 on Windows x64 and needs **Framework: Inspector**. See the [release guide](docs/releasing.md) for build and upgrade details.

## Publishing and forks

Follow the [Patchbay Launcher packaging contract](docs/patchbay-launcher.md)
for ZIP names, release assets, and adding a fork as a source.

## Devices

My native devices and modulators live in [bitwig-devices](https://github.com/polarity/bitwig-devices). Patchbay loads them through **Framework: Nitro Devices** (`custom-device-registrar`). To make your own, start with [Bitwig Device Blueprints](https://github.com/polarity/bitwig-devices-blueprint).

The mods were originally contributed to [shaboogen-dev/Mods](https://github.com/shaboogen-dev/Mods/commit/8a773ee3537fe9a873584f8c27f054bac21ca4f0). See [LICENSE](LICENSE).
