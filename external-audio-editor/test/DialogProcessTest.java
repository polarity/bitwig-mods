package com.bitwig.mods.externalaudio;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

/** Interactive regression test: launch with -Djava.awt.headless=true, then save or cancel the child dialog. */
public final class DialogProcessTest {
    public static void main(String[] args) throws Exception {
        if (!GraphicsEnvironment.isHeadless()) throw new AssertionError("The parent must be headless, as Bitwig is.");
        String expected = args[1];
        boolean cancel = args.length > 2 && args[2].equals("cancel");
        String result = DialogProcess.choose(Path.of(args[0]), expected, false);
        if (cancel ? result != null : !expected.equals(result)) throw new AssertionError("Unexpected dialog result: " + result);
        if (!GraphicsEnvironment.isHeadless()) throw new AssertionError("Parent headless state changed.");
        System.out.println("DIALOG_PROCESS_TEST_OK " + (cancel ? "cancel" : "save") + " parent=headless");
    }
}
