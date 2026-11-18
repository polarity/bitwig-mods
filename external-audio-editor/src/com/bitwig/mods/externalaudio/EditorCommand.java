package com.bitwig.mods.externalaudio;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Argument vectors only: neither editor paths nor sample names are interpreted by a shell. */
final class EditorCommand {
    private EditorCommand() {}

    static List<String> of(String editor, File source, String os) throws IOException {
        File application = validate(editor, os);
        if (!source.isFile()) throw new IOException("Sample file not found: " + source);
        if (isMac(os) && application.getName().toLowerCase(Locale.ROOT).endsWith(".app")) {
            return List.of("/usr/bin/open", "-a", application.getAbsolutePath(), source.getAbsolutePath());
        }
        return List.of(application.getAbsolutePath(), source.getAbsolutePath());
    }

    static File validate(String editor, String os) throws IOException {
        if (editor == null || editor.isBlank()) throw new IOException("Choose an external audio editor first.");
        File application = new File(editor.strip());
        if (!application.isAbsolute()) throw new IOException("Enter the full path to the editor application.");
        boolean bundle = isMac(os) && application.isDirectory()
                && application.getName().toLowerCase(Locale.ROOT).endsWith(".app");
        if (!bundle && !application.isFile()) throw new IOException("Editor application not found: " + application);
        if (!bundle && !os.toLowerCase(Locale.ROOT).startsWith("windows") && !application.canExecute()) {
            throw new IOException("The editor file is not executable: " + application);
        }
        return application;
    }

    static Process start(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        process.getOutputStream().close();
        return process;
    }

    private static boolean isMac(String os) {
        return os.toLowerCase(Locale.ROOT).startsWith("mac");
    }
}
