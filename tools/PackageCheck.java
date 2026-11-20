package dev.bitwigre.bml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Tests the released importer and inventory in an isolated portable loader directory.
 * Arguments: loader root, expected number of installed mods, then the ZIPs to import.
 */
public final class PackageCheck {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]); Path mods = root.resolve("mods");
        int expected = Integer.parseInt(args[1]);
        for (int i = 2; i < args.length; i++) {
            var checked = ModInstaller.check(Path.of(args[i]), mods);
            ModInstaller.install(checked, mods);
            var installed = mods.resolve(checked.id());
            for (String line : Files.readAllLines(installed.resolve("SHA256SUMS.txt"))) {
                var parts = line.split("  ", 2);
                var actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(installed.resolve(parts[1]))));
                if (!actual.equals(parts[0])) throw new AssertionError(checked.id() + ": " + parts[1]);
            }
        }
        var snapshot = PackageInventory.inspect(mods);
        var pack = MapPacks.packs(root.resolve("maps")).getFirst();
        PackageInventory.markUnavailable(snapshot, pack);
        if (snapshot.entries().size() != expected)
            throw new AssertionError("Expected " + expected + " mods and frameworks, found " + snapshot.entries().size());
        for (var entry : snapshot.entries()) {
            if (!entry.loadable()) throw new AssertionError(entry.id + ": " + entry.issueSummary());
        }
        System.out.println("IMPORT_CHECKSUMS_AVAILABLE_OK " + pack.targetKey() + " mods=" + expected);
    }
}
