"""Build release ZIPs using Patchbay's SDK and a stock Bitwig install."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile


PIANO_ROLL_MODS = ("scale-fold", "step-sequencer")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("patchbay-source", "patchbay-release", "bitwig-bin", "jdk-bin"):
        parser.add_argument("--" + option, required=True, type=Path)
    parser.add_argument("--out", type=Path, default=Path("dist"))
    parser.add_argument("--mod", choices=(*PIANO_ROLL_MODS, "repitch-transpose", "popup-live-preview"))
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
    for mod in mods:
        manifest = json.loads((repo / mod / "mod.json").read_text(encoding="utf-8"))
        version = manifest["built_against"]["bitwig_version"]
        if reference_build and version != reference_build:
            parser.error("select --mod when the mods target different Bitwig versions")
        reference_build = version
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
        for mod in mods:
            sys.argv = ["build_mod.py", mod, "--out", str(out)]
            result = build_mod.main()
            if result:
                return result
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
