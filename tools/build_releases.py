"""Build release ZIPs using Patchbay's SDK and a stock Bitwig install."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from zipfile import ZipFile

from check_package import check_packages


PIANO_ROLL_MODS = ("scale-fold", "step-sequencer")
# Generic widget child removal can detach the native Drum/Hybrid button before
# its single-child wrapper finishes updating (Step Sequencer 0.1.0-dev.5).
WIDGET_CHILD_REMOVERS = {
    "6.1.3": ("Xzi", "(Ldvm;)V"),
    "6.1.7": ("neH", "(LdwF;)V"),
}


def preflight(mod, bitwig_version, package, *, jdk_bin=None, bitwig_bin=None, modkit_jar=None, compile_jar=None, patchbay_release=None):
    if mod == "lock-states":
        if jdk_bin is None or bitwig_bin is None or modkit_jar is None or patchbay_release is None:
            raise SystemExit("PREFLIGHT_FAILED lock-states: JDK, Bitwig libraries, mod kit and released Patchbay loader are required")
        command = [sys.executable, str(Path(__file__).with_name("test_lock_states.py")),
                   "--jdk-bin", str(jdk_bin), "--bitwig-bin", str(bitwig_bin),
                   "--package", str(package), "--modkit-jar", str(modkit_jar),
                   "--patchbay-release", str(patchbay_release)]
        if compile_jar is not None:
            command += ["--compile-jar", str(compile_jar)]
        try:
            subprocess.run(command, check=True)
        except subprocess.CalledProcessError:
            raise SystemExit("PREFLIGHT_FAILED lock-states: packaged state/range/storage regressions failed; ZIP was not released") from None
        print("PREFLIGHT_OK lock-states: packaged capture, recall, note sequences, native ranges and document persistence", flush=True)
        return
    if mod not in PIANO_ROLL_MODS:
        return

    remover = WIDGET_CHILD_REMOVERS.get(bitwig_version)
    if remover is None:
        raise SystemExit(f"PREFLIGHT_FAILED {mod}: identify the widget child remover for Bitwig {bitwig_version}")

    # Patchbay derives uses.tsv from the compiled helper, including method handles.
    with ZipFile(package) as archive:
        try:
            uses = archive.read(f"{mod}/uses.tsv").decode("utf-8")
        except KeyError:
            raise SystemExit(f"PREFLIGHT_FAILED {mod}: package is missing uses.tsv") from None

    for row in uses.splitlines():
        parts = row.split("\t")
        if parts[0] == "M" and tuple(parts[2:4]) == remover:
            _, owner, name, descriptor, _ = parts
            raise SystemExit(
                f"PREFLIGHT_FAILED {mod}: {owner}.{name}{descriptor} can leave the Drum/Hybrid "
                "button detached. Keep the native child in its wrapper and add the custom button "
                "while building the mode strip."
            )
    print(f"PREFLIGHT_OK {mod}: no generic widget child removal")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("patchbay-source", "patchbay-release", "bitwig-bin", "jdk-bin"):
        parser.add_argument("--" + option, required=True, type=Path)
    parser.add_argument("--out", type=Path, default=Path("dist"))
    parser.add_argument("--verification-runtime", type=Path,
                        help="A Bitwig jre folder for offline preparation when --bitwig-bin has no jre next to it")
    parser.add_argument(
        "--mod", choices=(*PIANO_ROLL_MODS, "repitch-transpose", "popup-live-preview", "note-intervals", "chord-tools", "external-audio-editor", "lock-states")
    )
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    source, release, bitwig, jdk, out = (
        p.resolve() for p in (
            args.patchbay_source, args.patchbay_release, args.bitwig_bin,
            args.jdk_bin, args.out,
        )
    )
    mods = (args.mod,) if args.mod else PIANO_ROLL_MODS
    reference_build = None
    framework_jars = {}
    packages = {}
    for mod in mods:
        manifest = json.loads((repo / mod / "mod.json").read_text(encoding="utf-8"))
        version = manifest["built_against"]["bitwig_version"]
        if reference_build and version != reference_build:
            parser.error("select --mod when the mods target different Bitwig versions")
        reference_build = version
        package = out / f"{mod}-{manifest['mod']['version']}.zip"
        if package.exists():
            parser.error(f"{package} exists; choose a fresh --out to rebuild it")
        packages[mod] = package
        build = json.loads((repo / mod / "build.json").read_text(encoding="utf-8"))
        for dependency in build.get("classpath_mods", []):
            framework_id, expected_version = dependency.split("@", 1)
            framework = release / "mods" / framework_id
            actual_version = json.loads((framework / "mod.json").read_text(encoding="utf-8"))["mod"]["version"]
            if actual_version != expected_version:
                parser.error(f"{mod} expects {dependency}; the Patchbay bundle has {framework_id}@{actual_version}")
            framework_jars[f"{framework_id}-{actual_version}.zip"] = framework / "helper.jar"

    kit = {name: release / "modkit" / name for name in ("bml-api.jar", "bml-modkit.jar")}
    map_info = json.loads((release / f"maps/windows-{reference_build}-map1/map.json").read_text(encoding="utf-8"))
    if hashlib.sha256((bitwig / "bitwig.jar").read_bytes()).hexdigest() != map_info["target_jar_sha256"]:
        parser.error(f"--bitwig-bin must point to stock Bitwig Studio {reference_build} on Windows")

    with tempfile.TemporaryDirectory(prefix="polarity-mods-") as tmp:
        workspace = Path(tmp)
        (workspace / "workspace.json").write_text(
            json.dumps({"mods": str(repo), "bitwig_jars": {reference_build: str(bitwig)}}), encoding="utf-8"
        )
        os.environ["BML_WORKSPACE"] = str(workspace)
        sys.path.insert(0, str(source / "tools"))
        import build_mod

        # Patchbay's builder pins the JDK to a local install and expects framework release ZIPs.
        # Use the same compiler version and read the framework JARs from the tester bundle.
        build_mod.JDK = jdk
        build_mod.modkit.JDK = jdk
        build_mod.modkit.CACHE = workspace / "compile-cache"
        build_mod.modkit.ensure = lambda: kit
        release_member = build_mod.release_member

        def read_release_member(path, name):
            if path.name in framework_jars and name == "helper.jar":
                return framework_jars[path.name].read_bytes()
            return release_member(path, name)

        build_mod.release_member = read_release_member
        out.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix=".build-", dir=out) as staging_dir:
            staging = Path(staging_dir)
            for mod, package in packages.items():
                sys.argv = ["build_mod.py", mod, "--out", str(staging)]
                result = build_mod.main()
                if result:
                    return result
                compile_jar = workspace / "compile-cache" / reference_build / f"bitwig{reference_build.replace('.', '')}-compile.jar"
                preflight(mod, reference_build, staging / package.name, jdk_bin=jdk, bitwig_bin=bitwig,
                            modkit_jar=kit["bml-modkit.jar"], compile_jar=compile_jar, patchbay_release=release)

            # Lock States' own preflight already imports and prepares its ZIP.
            unchecked = [staging / package.name for mod, package in packages.items() if mod != "lock-states"]
            if unchecked:
                try:
                    check_packages(unchecked, patchbay_release=release, jdk_bin=jdk, bitwig_bin=bitwig,
                                   map_directory=release / f"maps/windows-{reference_build}-map1", out=workspace / "package-check",
                                   verification_runtime=args.verification_runtime)
                except subprocess.CalledProcessError:
                    raise SystemExit("PREFLIGHT_FAILED import or offline preparation, see the output above; no ZIP was released") from None
                except AssertionError as failure:
                    raise SystemExit(f"PREFLIGHT_FAILED map or Bitwig JAR checksum: {failure}; no ZIP was released") from None
                print("PREFLIGHT_OK import, checksums, availability and offline preparation: "
                      + ", ".join(mod for mod in packages if mod != "lock-states"), flush=True)

            for package in packages.values():
                (staging / package.name).rename(package)
                print(f"READY {package}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
