package com.bitwig.mods.externalaudio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** The only bridge between Bitwig's JVM and the standalone path dialog. No AWT dependencies. */
final class DialogProcess {
    static final String PATH_PREFIX = "BML_EDITOR_PATH=";

    private DialogProcess() {}

    static List<String> command(Path jar, String current, boolean openAfter) throws IOException {
        if (!Files.isRegularFile(jar)) throw new IOException("Editor dialog JAR not found: " + jar);
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        Path java = bin.resolve(windows ? "javaw.exe" : "java");
        if (!Files.isRegularFile(java) && windows) java = bin.resolve("java.exe");
        if (!Files.isRegularFile(java)) throw new IOException("Java runtime not found: " + java);
        return List.of(java.toString(), "-Djava.awt.headless=false", "-cp", jar.toAbsolutePath().toString(),
                "com.bitwig.mods.externalaudio.EditorDialogMain", current, Boolean.toString(openAfter));
    }

    static String choose(Path jar, String current, boolean openAfter) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command(jar, current, openAfter)).redirectErrorStream(true).start();
        try {
            process.getOutputStream().close();
            byte[] output = process.getInputStream().readNBytes(65_537);
            if (output.length > 65_536) throw new IOException("The editor dialog returned too much output.");
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IOException("The editor dialog did not close.");
            String text = new String(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("Dialog exited with code " + process.exitValue() + ": "
                        + text.substring(0, Math.min(400, text.length())).strip());
            }
            for (String line : text.lines().toList()) {
                if (line.startsWith(PATH_PREFIX)) {
                    try {
                        return new String(Base64.getDecoder().decode(line.substring(PATH_PREFIX.length())), StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException invalid) {
                        throw new IOException("The editor dialog returned an invalid path.", invalid);
                    }
                }
            }
            return null;
        } finally {
            if (process.isAlive()) process.destroy();
        }
    }
}
