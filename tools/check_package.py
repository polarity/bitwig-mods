"""Import release ZIPs with the released Patchbay loader and prepare them offline.

Nothing touches the user's Patchbay install and Bitwig is not started.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile


def frameworks_of(package):
    """The framework IDs a package's build file pins, read from the ZIP itself."""
    with ZipFile(package) as archive:
        build = next(name for name in archive.namelist() if name.count("/") == 1 and name.endswith("/build.json"))
        return [dependency.split("@", 1)[0] for dependency in json.loads(archive.read(build)).get("classpath_mods", [])]


def check_packages(packages, *, patchbay_release, jdk_bin, bitwig_bin, map_directory, out, verification_runtime=None):
    """Stages an isolated portable loader in the fresh directory `out`, imports the packages and prepares them.

    Returns the staged root, whose loader/mod-runtime holds the prepared helpers. Raises CalledProcessError on failure.
    """
    root = Path(out).resolve()
    root.mkdir()  # require a fresh isolated test directory
    metadata = json.loads((map_directory / "map.json").read_text(encoding="utf-8"))
    for filename, digest in metadata["file_sha256"].items():
        assert hashlib.sha256((map_directory / filename).read_bytes()).hexdigest() == digest, filename
    assert hashlib.sha256((bitwig_bin / "bitwig.jar").read_bytes()).hexdigest() == metadata["target_jar_sha256"]
    staged_bin = Path(bitwig_bin).resolve()
    if verification_runtime:
        staged_bin = root / "bitwig/bin"
        staged_bin.mkdir(parents=True)
        for filename in ("bitwig.jar", "libs.jar", "lwjgl.jar"):
            if (bitwig_bin / filename).is_file():
                shutil.copy2(bitwig_bin / filename, staged_bin / filename)
        shutil.copytree(verification_runtime, root / "bitwig/jre")
    for name in ("app", "tools", "api", "diagnostics"):
        shutil.copytree(patchbay_release / "loader" / name, root / "loader" / name)
    shutil.copytree(map_directory, root / "maps" / map_directory.name)
    (root / "config").mkdir()
    (root / "config/portable").touch()
    (root / "mods").mkdir()
    frameworks = sorted({framework for package in packages for framework in frameworks_of(package)})
    for name in frameworks:
        shutil.copytree(patchbay_release / "mods" / name, root / "mods" / name)
    suffix = ".exe" if os.name == "nt" else ""
    cp = root / "loader/app/bml-control.jar"
    subprocess.run([jdk_bin / ("javac" + suffix), "-cp", cp, "-d", root / "classes",
                    Path(__file__).with_name("PackageCheck.java")], check=True)
    subprocess.run([jdk_bin / ("java" + suffix), "-cp", str(cp) + os.pathsep + str(root / "classes"),
                    "dev.bitwigre.bml.PackageCheck", root, str(len(frameworks) + len(packages)),
                    *(Path(package).resolve() for package in packages)], check=True)
    with (root / "prepare.log").open("w", encoding="utf-8") as log:
        result = subprocess.run([jdk_bin / ("java" + suffix), "-cp", cp, "dev.bitwigre.bml.JavaPreparer",
                                 "--loader-root", root / "loader", "--mods-root", root / "mods",
                                 "--bitwig-bin", staged_bin], stdout=log, stderr=subprocess.STDOUT)
    lines = (root / "prepare.log").read_text(encoding="utf-8").splitlines()
    if result.returncode:  # the failure is rarely in the last lines; show everything except passed verifications
        print("\n".join(line for line in lines if not line.startswith("DEEP_VERIFY_OK")), flush=True)
    else:
        print("\n".join(line for line in lines if line.startswith(("PREPARE_OK", "Mods:"))), flush=True)
    result.check_returncode()
    return root


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", type=Path, required=True, action="append", help="Repeat to check several ZIPs together")
    for name in ("patchbay-release", "jdk-bin", "bitwig-bin", "map-directory", "out"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--verification-runtime", type=Path,
                        help="Stage the target JARs with a runtime executable on this host for cross-platform offline checks")
    args = parser.parse_args()
    root = check_packages(args.package, patchbay_release=args.patchbay_release, jdk_bin=args.jdk_bin, bitwig_bin=args.bitwig_bin,
                          map_directory=args.map_directory, out=args.out, verification_runtime=args.verification_runtime)
    print("PACKAGE_OK", root, flush=True)


if __name__ == "__main__":
    main()
