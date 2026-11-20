package com.bitwig.mods.scalpelstems;

import com.bitwig.mods.audioanalysis.Platform;
import com.bitwig.mods.audioanalysis.Ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class CudaFolderPicker {
    private static final AtomicBoolean OPEN = new AtomicBoolean();

    private CudaFolderPicker() {}

    static void show(Path jar, Path initial, Consumer<Path> onSelection) {
        if (!OPEN.compareAndSet(false, true)) return;
        Thread.ofPlatform().daemon(true).name("scalpel-cuda-folder").start(() -> {
            try {
                Path selected = choose(jar, initial);
                if (selected != null) Ui.later(() -> onSelection.accept(selected));
            } catch (IOException | RuntimeException error) {
                Ui.notify("Could not open the CUDA folder chooser: " + error.getMessage());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                OPEN.set(false);
            }
        });
    }

    private static Path choose(Path jar, Path initial) throws IOException, InterruptedException {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        boolean windows = Platform.current() == Platform.Os.WINDOWS;
        Path java = bin.resolve(windows ? "javaw.exe" : "java");
        if (windows && !Files.isRegularFile(java)) java = bin.resolve("java.exe");
        Process process = new ProcessBuilder(java.toString(), "-Djava.awt.headless=false", "-cp", jar.toString(),
            CudaFolderDialog.class.getName(), initial.toString()).redirectErrorStream(true).start();
        try {
            process.getOutputStream().close();
            byte[] output = process.getInputStream().readNBytes(65_537);
            if (output.length > 65_536) throw new IOException("The folder chooser returned too much output");
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IOException("The folder chooser did not close");
            String text = new String(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("The folder chooser exited with status " + process.exitValue() + ". "
                    + text.lines().findFirst().orElse(""));
            }
            for (String line : text.lines().toList()) {
                if (line.startsWith(CudaFolderDialog.PATH_PREFIX)) {
                    byte[] path = Base64.getDecoder().decode(line.substring(CudaFolderDialog.PATH_PREFIX.length()));
                    return Path.of(new String(path, StandardCharsets.UTF_8));
                }
            }
            return null;
        } finally {
            if (process.isAlive()) process.destroy();
        }
    }
}
