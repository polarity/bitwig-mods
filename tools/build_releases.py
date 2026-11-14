"""Build both release ZIPs using Patchbay's SDK and a stock Bitwig 6.1.3 install."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("patchbay-source", "patchbay-release", "bitwig-bin", "jdk-bin"):
        parser.add_argument("--" + option, required=True, type=Path)
    parser.add_argument("--out", type=Path, default=Path("dist"))
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    source, release, bitwig, jdk, out = (
        p.resolve() for p in (
            args.patchbay_source, args.patchbay_release, args.bitwig_bin,
            args.jdk_bin, args.out,
        )
    )
    kit = {name: release / "modkit" / name for name in ("bml-api.jar", "bml-modkit.jar")}
    framework = release / "mods/piano-roll-framework"
    map_info = json.loads((release / "maps/windows-6.1.3-map1/map.json").read_text(encoding="utf-8"))
    if hashlib.sha256((bitwig / "bitwig.jar").read_bytes()).hexdigest() != map_info["target_jar_sha256"]:
        parser.error("--bitwig-bin must point to stock Bitwig Studio 6.1.3 on Windows")
    framework_version = json.loads((framework / "mod.json").read_text(encoding="utf-8"))["mod"]["version"]
    expected = f"piano-roll-framework@{framework_version}"
    deps = json.loads((repo / "step-sequencer/build.json").read_text(encoding="utf-8"))["classpath_mods"]
    if deps != [expected]:
        parser.error(f"the step sequencer expects {deps[0]}; the Patchbay bundle has {expected}")

    with tempfile.TemporaryDirectory(prefix="polarity-mods-") as tmp:
        workspace = Path(tmp)
        (workspace / "workspace.json").write_text(
            json.dumps({"mods": str(repo), "bitwig_jars": {"6.1.3": str(bitwig)}}), encoding="utf-8"
        )
        os.environ["BML_WORKSPACE"] = str(workspace)
        sys.path.insert(0, str(source / "tools"))
        import build_mod

        # Patchbay's builder pins the JDK to a local install and expects framework release ZIPs.
        # Use the same compiler version and read the framework JAR from the tester bundle.
        build_mod.JDK = jdk
        build_mod.modkit.JDK = jdk
        build_mod.modkit.CACHE = workspace / "compile-cache"
        build_mod.modkit.ensure = lambda: kit
        release_member = build_mod.release_member

        def read_release_member(path, name):
            if path.name == f"piano-roll-framework-{framework_version}.zip" and name == "helper.jar":
                return (framework / name).read_bytes()
            return release_member(path, name)

        build_mod.release_member = read_release_member
        for mod in ("scale-fold", "step-sequencer"):
            sys.argv = ["build_mod.py", mod, "--out", str(out)]
            result = build_mod.main()
            if result:
                return result
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
