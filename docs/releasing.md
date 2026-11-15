# Build and verify release ZIPs

## Release workflow

1. [Build the mod ZIP locally](#local-build), verify Patchbay import and offline preparation, and give the ZIP to Robert.
2. Robert installs the ZIP through Patchbay and tests the mod in Bitwig. If a fix is needed, rebuild and send the updated ZIP for another test.
3. After Robert confirms the build in Bitwig, publish that ZIP as an asset on the GitHub release.
4. Draft a short English Discord announcement that explains what the mod does and links to the GitHub release.

## Local build

Build the tester ZIPs on Windows x64 with Python 3, JDK 25.0.4.1, and the extracted [Patchbay 0.36.0-dev.14 Windows bundle](https://github.com/shaboogen-dev/Patchbay/releases/tag/0.36.0-dev.14). Check out the [matching Patchbay source tag](https://github.com/shaboogen-dev/Patchbay/tree/0.36.0-dev.14) for the SDK builder.

From the repository root, run the command with your own paths. The default builds Scale Fold and Step Sequencer. Point `--bitwig-bin` at stock Bitwig Studio 6.1.3 for these two mods.

~~~powershell
python tools/build_releases.py --patchbay-source C:/work/Patchbay --patchbay-release C:/work/BML-0.36.0-dev.14-windows --bitwig-bin "C:/Program Files/Bitwig Studio/bin" --jdk-bin C:/work/jdk-25.0.4.1/bin
~~~

To build Repitch Transpose, pass `--mod repitch-transpose` and point `--bitwig-bin` at stock Bitwig Studio 6.1.7. The bundle must contain Inspector Framework 0.3.0-dev.2.

To build Remember Live Preview, pass `--mod popup-live-preview` and use stock Bitwig Studio 6.1.7 and Settings Framework 1.0.0.

The builder generates one versioned ZIP per mod in `dist/`. Use `--out` to choose a fresh folder for a repeat build. Each ZIP includes the compiled helper, generated manifest, sources and checksums. Use **Mods → Add Mod…** in Patchbay to import the ZIP.

## Installing or replacing tester builds

Leave the individual release packages zipped for import. GitHub's automatically generated **Source code** archive does not include the compiled helper JARs. If Patchbay offers to replace an existing copy, accept. If you installed a source folder under a different name, move it out of Patchbay's `mods` directory first.

The Scale Fold and Step Sequencer ZIPs passed import and offline preparation together on Bitwig Studio 6.1.3 Windows x64.
