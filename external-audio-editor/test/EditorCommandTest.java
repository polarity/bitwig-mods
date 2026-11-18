package com.bitwig.mods.externalaudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Standalone tests: no Bitwig, Patchbay or editor installation required. */
public final class EditorCommandTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("external editor ü & ");
        File sample = Files.writeString(root.resolve("take ü & $name ' 01.wav"), "audio").toFile();
        File executable = Files.writeString(root.resolve("Editor with spaces.exe"), "test").toFile();
        executable.setExecutable(true);
        File app = Files.createDirectory(root.resolve("Audio Editor.app")).toFile();
        check(EditorCommand.of(executable.toString(), sample, "Windows 11")
                .equals(List.of(executable.toString(), sample.toString())), "Windows arguments");
        check(EditorCommand.of(executable.toString(), sample, "Linux")
                .equals(List.of(executable.toString(), sample.toString())), "Linux arguments");
        check(EditorCommand.of(app.toString(), sample, "Mac OS X")
                .equals(List.of("/usr/bin/open", "-a", app.toString(), sample.toString())), "macOS bundle");
        check(EditorCommand.of(executable.toString(), sample, "Mac OS X")
                .equals(List.of(executable.toString(), sample.toString())), "macOS executable");
        reject(() -> EditorCommand.of("", sample, "Windows"));
        reject(() -> EditorCommand.of("editor.exe", sample, "Windows"));
        reject(() -> EditorCommand.of(root.resolve("missing.exe").toString(), sample, "Windows"));
        reject(() -> EditorCommand.of(root.toString(), sample, "Windows"));
        reject(() -> EditorCommand.of(app.toString(), sample, "Linux"));
        reject(() -> EditorCommand.of(executable.toString(), root.resolve("missing.wav").toFile(), "Windows"));
        SourceFiles sources = new SourceFiles();
        reject(sources::single);
        reject(() -> sources.add(null));
        sources.add(sample);
        sources.add(new File(root.toFile(), "." + File.separator + sample.getName()));
        check(sources.single().equals(sample.getCanonicalFile()), "same source in several regions");
        sources.add(root.resolve("another.wav").toFile());
        reject(sources::single);

        if (args.length == 1) {
            Path output = Path.of(sample + ".received");
            // EditorProbe.cs acts as the editor and records the sole file argument it receives.
            Process process = EditorCommand.start(EditorCommand.of(args[0], sample, System.getProperty("os.name")));
            check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0, "process launch");
            check(Files.readString(output).equals("1\n" + sample), "exact path received by editor");
        }
        System.out.println("EDITOR_COMMAND_TEST_OK (arguments, validation, source ambiguity"
                + (args.length == 1 ? ", real process" : "") + ")");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void reject(Checked action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected an IOException");
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
