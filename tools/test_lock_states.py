"""Test Lock States' core from source or from the helper JAR in an actual release ZIP."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from zipfile import ZipFile


def run_tests(jdk_bin, bitwig_bin, package=None, *, modkit_jar=None, compile_jar=None, patchbay_release=None):
    if modkit_jar is not None and package is None:
        raise ValueError("Native canaries require a packaged helper JAR")
    if modkit_jar is not None and patchbay_release is None:
        raise ValueError("Native document persistence canaries require --patchbay-release")
    repo = Path(__file__).resolve().parent.parent
    source = repo / "lock-states/src/com/bitwig/mods/lockstates"
    with tempfile.TemporaryDirectory(prefix="lock-states-tests-") as temporary:
        workspace = Path(temporary)
        classes = workspace / "classes"
        suffix = ".exe" if os.name == "nt" else ""
        dependencies = [str(bitwig_bin / "libs.jar")]
        production_sources = [str(source / name) for name in
                              ("StateBank.java", "BankStore.java", "Recall.java", "IntegerRange.java", "DeviceStates.java", "DeviceIdentity.java", "StateMigration.java", "SnapshotPaths.java", "NoteAssignments.java", "SequencePlan.java", "CurveSpec.java")]
        if package is not None:
            with ZipFile(package) as archive:
                manifest = json.loads(archive.read("lock-states/mod.json"))
                if manifest["mod"]["id"] != "lock-states":
                    raise ValueError("Expected a Lock States package")
                helper_bytes = archive.read("lock-states/helper.jar")
            helper = workspace / "helper.jar"
            helper.write_bytes(helper_bytes)
            dependencies.insert(0, str(helper))
            production_sources = []  # Never replace the packaged implementation with fresh source classes.
        subprocess.run([str(jdk_bin / ("javac" + suffix)), "--release", "21", "-encoding", "UTF-8", "-proc:none",
                        "-cp", os.pathsep.join(dependencies), "-d", str(classes), *production_sources,
                        str(repo / "lock-states/test/StateTests.java")], check=True)
        subprocess.run([str(jdk_bin / ("java" + suffix)), "-ea", "-cp",
                        os.pathsep.join([str(classes), *dependencies]),
                        "com.bitwig.mods.lockstates.StateTests"], check=True)
        if modkit_jar is not None:
            run_native_tests(jdk_bin, bitwig_bin, modkit_jar, compile_jar,
                             workspace, [str(classes), *dependencies])
            command = [sys.executable, str(repo / "tools/check_lock_states.py"),
                       "--package", str(package.resolve()), "--patchbay-release", str(patchbay_release),
                       "--jdk-bin", str(jdk_bin), "--bitwig-bin", str(bitwig_bin),
                       "--map-directory", str(patchbay_release / "maps/windows-6.1.4-map1"),
                       "--out", str(workspace / "native-storage"), "--modkit-jar", str(modkit_jar)]
            if compile_jar is not None:
                command += ["--compile-jar", str(compile_jar)]
            subprocess.run(command, check=True)
        if package is not None:
            print("LOCK_STATES_PREFLIGHT_OK version=" + manifest["mod"]["version"]
                  + " helper_sha256=" + hashlib.sha256(helper_bytes).hexdigest(), flush=True)


def run_native_tests(jdk_bin, bitwig_bin, modkit_jar, compile_jar, workspace, dependencies):
    """Typed native probes target the SDK's Windows 6.1.4 reference, not arbitrary remapped JARs."""
    if hashlib.sha256((bitwig_bin / "bitwig.jar").read_bytes()).hexdigest() != (
            "5c38187eebe14ce7e5fdaa9d8f1eac0e9b2a34116f57d6a854cef7a31f2845c0"):
        raise ValueError("Native range canaries require the stock Windows 6.1.4 reference JAR")
    suffix = ".exe" if os.name == "nt" else ""
    java = str(jdk_bin / ("java" + suffix))
    if compile_jar is None:
        compile_jar = workspace / "native-compile.jar"
        subprocess.run([java, "-jar", str(modkit_jar), "compile-jar", str(bitwig_bin / "bitwig.jar"),
                        str(compile_jar)], check=True)
    native_classes = workspace / "native-classes"
    native_source = Path(__file__).resolve().parent.parent / "lock-states/test/NativeRangeTests.java"
    subprocess.run([str(jdk_bin / ("javac" + suffix)), "--release", "21", "-proc:none", "-encoding", "UTF-8",
                    "-cp", os.pathsep.join([str(compile_jar), *dependencies]), "-d", str(native_classes),
                    str(native_source)], check=True)
    subprocess.run([java, "-jar", str(modkit_jar), "finish", str(native_classes),
                    str(workspace / "native-hooks.json"), str(bitwig_bin / "bitwig.jar"),
                    str(bitwig_bin / "libs.jar"), *(path for path in dependencies if Path(path).is_file())], check=True)
    subprocess.run([java, "-Djava.awt.headless=true", "-DinstallationRoot=" + str(bitwig_bin.parent),
                    "-cp", os.pathsep.join([str(native_classes), *dependencies, str(bitwig_bin / "bitwig.jar")]),
                    "com.bitwig.flt.control_surface.values.NativeRangeTests"], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk-bin", type=Path, required=True)
    parser.add_argument("--bitwig-bin", type=Path, required=True)
    parser.add_argument("--package", type=Path, help="Run the regressions against this ZIP's helper, not source")
    parser.add_argument("--modkit-jar", type=Path, help="Also run native range canaries against the Windows 6.1.4 reference")
    parser.add_argument("--compile-jar", type=Path, help="Reuse an SDK compile JAR for native canaries")
    parser.add_argument("--patchbay-release", type=Path, help="Released loader/frameworks for native persistence canaries")
    args = parser.parse_args()
    if args.compile_jar and not args.modkit_jar:
        parser.error("--compile-jar requires --modkit-jar")
    if args.modkit_jar and not args.package:
        parser.error("--modkit-jar requires --package")
    run_tests(args.jdk_bin, args.bitwig_bin, args.package,
              modkit_jar=args.modkit_jar, compile_jar=args.compile_jar, patchbay_release=args.patchbay_release)


if __name__ == "__main__":
    main()
