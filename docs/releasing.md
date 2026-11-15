# Build and verify release ZIPs

This is the workflow used for the tester ZIPs targeting Bitwig Studio 6.1.3 on Windows x64.

Use Python 3, JDK 25.0.4.1, a stock Windows x64 installation of Bitwig Studio 6.1.3, and the extracted [Patchbay 0.36.0-dev.14 Windows bundle](https://github.com/shaboogen-dev/Patchbay/releases/tag/0.36.0-dev.14). Check out the [matching Patchbay source tag](https://github.com/shaboogen-dev/Patchbay/tree/0.36.0-dev.14) for the SDK builder.

From the repository root, run the command with your own paths.

~~~powershell
python tools/build_releases.py --patchbay-source C:/work/Patchbay --patchbay-release C:/work/BML-0.36.0-dev.14-windows --bitwig-bin "C:/Program Files/Bitwig Studio/bin" --jdk-bin C:/work/jdk-25.0.4.1/bin
~~~

The builder generates one versioned ZIP per mod in `dist/`. Use `--out` to choose a fresh folder for a repeat build. Each ZIP includes the compiled helper, generated manifest, sources and checksums. Verify the ZIPs through **Mods → Add Mod…** and offline preparation before attaching the two files to a GitHub release.

## Installing or replacing tester builds

Leave the individual release packages zipped for import. GitHub's automatically generated **Source code** archive does not include the compiled helper JARs. If Patchbay offers to replace an existing copy, accept. If you installed a source folder under a different name, move it out of Patchbay's `mods` directory first.

Both ZIPs passed import and offline preparation together on the Windows x64 setup above. Other Bitwig versions and platforms were not verified for this release.
