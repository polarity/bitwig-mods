# Polarity's Bitwig Mods

Two mods for the Bitwig Studio Detail Editor, made for [Patchbay Mod Loader](https://github.com/shaboogen-dev/Patchbay).

| Mod | What it adds |
| --- | --- |
| [Piano Roll: Scale Fold](scale-fold/README.md) | A compact note grid that shows every pitch in the current scale. |
| [Piano Roll: Step Sequencer](step-sequencer/README.md) | A sixteenth-note grid for drawing steps and editing note parameters. |

## Install

1. Get [Patchbay 0.36.0-dev.14](https://github.com/shaboogen-dev/Patchbay/releases/tag/0.36.0-dev.14).
2. Download the individual mod ZIPs from [Releases](https://github.com/polarity/bitwig-mods/releases). Leave them zipped. GitHub's automatically generated **Source code** archive does not include the compiled helper JARs.
3. In Patchbay, select **Mods → Add Mod…** and pick the ZIPs. If Patchbay offers to replace an existing copy, accept. If a source folder was installed under a different name, move that folder out of Patchbay's `mods` directory first.
4. Enable the mods and click **Load**. Step Sequencer also needs the bundled **Framework: Piano Roll** enabled.

These are tester builds. Both ZIPs passed Patchbay's import and offline preparation together on Windows x64 with Bitwig Studio 6.1.3. Other Bitwig versions and platforms were not verified for this release.

## Build release ZIPs

Use Python 3, JDK 25.0.4.1, a stock Windows x64 installation of Bitwig Studio 6.1.3, and the extracted Patchbay 0.36.0-dev.14 Windows bundle. Check out the [matching Patchbay source tag](https://github.com/shaboogen-dev/Patchbay/tree/0.36.0-dev.14) for the SDK builder.

From this repository, run the following with your own paths:

~~~powershell
python tools/build_releases.py --patchbay-source C:/work/Patchbay --patchbay-release C:/work/BML-0.36.0-dev.14-windows --bitwig-bin "C:/Program Files/Bitwig Studio/bin" --jdk-bin C:/work/jdk-25.0.4.1/bin
~~~

The builder generates one versioned ZIP per mod in `dist/`. Use `--out` to choose a fresh folder for a repeat build. Each ZIP includes the compiled helper, generated manifest, sources and checksums. Verify the ZIPs through **Add Mod…** and offline preparation before attaching the two files to a GitHub release.

The mods were originally contributed to [shaboogen-dev/Mods](https://github.com/shaboogen-dev/Mods/commit/8a773ee3537fe9a873584f8c27f054bac21ca4f0). See [LICENSE](LICENSE).
