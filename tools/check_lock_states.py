"""Check a Lock States ZIP without installing into the user's Patchbay or launching Bitwig."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
from check_package import check_packages
from test_lock_states import run_tests


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("package", "patchbay-release", "jdk-bin", "bitwig-bin", "map-directory", "out"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--verification-runtime", type=Path,
                        help="Stage the target JARs with a runtime executable on this host for cross-platform offline checks")
    parser.add_argument("--modkit-jar", type=Path, help="Also run native document persistence canaries (Windows 6.1.4 reference only)")
    parser.add_argument("--compile-jar", type=Path, help="Reuse the reference SDK compile JAR for native persistence canaries")
    args = parser.parse_args()
    if args.compile_jar and not args.modkit_jar:
        parser.error("--compile-jar requires --modkit-jar")
    run_tests(args.jdk_bin, args.bitwig_bin, args.package)
    root = check_packages([args.package], patchbay_release=args.patchbay_release, jdk_bin=args.jdk_bin,
                          bitwig_bin=args.bitwig_bin, map_directory=args.map_directory, out=args.out,
                          verification_runtime=args.verification_runtime)
    if args.modkit_jar:
        run_storage_tests(args, root)
    print("LOCK_STATES_PACKAGE_OK", root, flush=True)


def run_storage_tests(args, root):
    if hashlib.sha256((args.bitwig_bin / "bitwig.jar").read_bytes()).hexdigest() != (
            "5c38187eebe14ce7e5fdaa9d8f1eac0e9b2a34116f57d6a854cef7a31f2845c0"):
        raise ValueError("Native document canaries require the stock Windows 6.1.4 reference JAR")
    suffix = ".exe" if os.name == "nt" else ""
    java = str(args.jdk_bin / ("java" + suffix))
    compile_jar = args.compile_jar or root / "storage-compile.jar"
    if not args.compile_jar:
        subprocess.run([java, "-jar", str(args.modkit_jar), "compile-jar", str(args.bitwig_bin / "bitwig.jar"), str(compile_jar)], check=True)
    runtime = root / "loader/mod-runtime"
    helper = runtime / "helper.jar"
    # Restore bitwig.obf aliases in the packaged helper's signatures for javac.
    # This is a compile-only stub; execution below always uses the real prepared helper.
    compile_helper = root / "storage-helper-signatures.jar"
    subprocess.run([java, "-jar", str(args.modkit_jar), "compile-jar", str(helper), str(compile_helper)], check=True)
    libraries = args.bitwig_bin / "libs.jar"
    classes = root / "storage-test-classes"
    source = Path(__file__).resolve().parent.parent / "lock-states/test/NativeStorageTests.java"
    subprocess.run([args.jdk_bin / ("javac" + suffix), "--release", "21", "-encoding", "UTF-8", "-proc:none",
                    "-cp", os.pathsep.join(map(str, [compile_jar, compile_helper, libraries])), "-d", classes, source], check=True)
    subprocess.run([java, "-jar", str(args.modkit_jar), "finish", str(classes), str(root / "storage-test-hooks.json"),
                    str(args.bitwig_bin / "bitwig.jar"), str(libraries), str(helper)], check=True)
    # Test-only access to native end-event maintenance, named by a typed SDK
    # reference in NativeStorageTests rather than an obfuscated lookup string.
    access = json.loads((root / "storage-test-hooks.json").read_text(encoding="utf-8"))["access"]
    repairs = [item["name"] for item in access if item["kind"] == "method" and item["owner"] == "GVR" and item["descriptor"] == "()V"]
    if len(repairs) != 1:
        raise ValueError("Expected one typed native arrangement-maintenance canary reference")
    classpath = os.pathsep.join(map(str, [classes, runtime / "deep-verify-replacements.jar", helper,
                                        args.bitwig_bin / "bitwig.jar", libraries, root / "loader/api/bml-api.jar"]))
    command = [java, "-ea:com.bitwig.mods.lockstates...", "-Djava.awt.headless=true",
               "-Dlockstates.canary.repair=" + repairs[0],
               "-DinstallationRoot=" + str(args.bitwig_bin.parent), "-cp", classpath,
               "com.bitwig.mods.lockstates.NativeStorageTests"]
    subprocess.run([*command, "write", str(root / "document-canary")], check=True, timeout=60)
    subprocess.run([*command, "read", str(root / "document-canary")], check=True, timeout=60)


if __name__ == "__main__":
    main()
