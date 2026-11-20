# Polarity's Bitwig Mods

This repository develops Polarity's application mods for Bitwig Studio. Authors maintain their mods in their own repositories and distribute packages that [Patchbay Mod Loader](https://github.com/shaboogen-dev/Patchbay) can load.

| Task | Start here |
| --- | --- |
| Develop or change a mod | [Patchbay SDK guide](https://github.com/shaboogen-dev/Patchbay/blob/main/docs/sdk/README.md) for the mod kit and frameworks. Use [shaboogen-dev/Mods](https://github.com/shaboogen-dev/Mods) for working examples and package layout. |
| Place a hook, read Bitwig's code, or build Inspector widgets the frameworks lack | [Mod development notes](docs/mod-development.md): pitfalls and working patterns found in this repository. |
| Work on an existing mod | [Scale Fold](scale-fold/README.md), [Step Sequencer](step-sequencer/README.md), [Note Intervals](note-intervals/README.md), [Chord Tools](chord-tools/README.md), [Repitch Transpose](repitch-transpose/README.md), [External Audio Editor](external-audio-editor/README.md), [Remember Live Preview](popup-live-preview/README.md), or [Lock States](lock-states/README.md). Each mod directory contains its own `mod.json`, `build.json`, and `src/`. |
| Build or verify release ZIPs | [Release guide](docs/releasing.md) and [local build script](tools/build_releases.py). |
| Publish mods for Patchbay Launcher or configure a fork | Follow the [shared packaging manifest](docs/patchbay-launcher.md#mods). |
| Install native devices or modulators | [Polarity's Bitwig Devices](https://github.com/polarity/bitwig-devices#install). Patchbay registers these through **Framework: Nitro Devices** (`custom-device-registrar`). |
| Develop a native Bitwig device | [Bitwig Device Blueprints agent guide](https://github.com/polarity/bitwig-devices-blueprint/blob/main/AGENTS.md) for a working example and the build workflow. |

For documentation changes, follow the shared documentation standard at `F:/GitHub/coding.defaults/documentation-standard.md` when available. This table is the documentation index.

Maintain the shared launcher manifest in this repository and copy it verbatim
to `docs/patchbay-launcher.md` in [bitwig-devices](https://github.com/polarity/bitwig-devices)
when updating it.
