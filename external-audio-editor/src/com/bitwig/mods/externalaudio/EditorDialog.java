package com.bitwig.mods.externalaudio;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bitwig runs headless: keep all AWT/Swing code in the separate dialog process. */
final class EditorDialog {
    private static final AtomicBoolean OPEN = new AtomicBoolean();

    private EditorDialog() {}

    static void show(File sourceToOpen) {
        if (!OPEN.compareAndSet(false, true)) return;
        Thread.ofPlatform().daemon(true).name("external-editor-settings").start(() -> {
            try {
                String selected = DialogProcess.choose(ExternalAudioEditor.dialogJar(),
                        ExternalAudioEditor.editor(), sourceToOpen != null);
                if (selected == null) return;
                EditorCommand.validate(selected, System.getProperty("os.name", ""));
                ExternalAudioEditor.setEditor(selected);
                if (sourceToOpen != null) ExternalAudioEditor.launch(sourceToOpen);
            } catch (IOException | RuntimeException | LinkageError error) {
                ExternalAudioEditor.fail("Could not open the editor settings: " + error.getMessage(), error);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                OPEN.set(false);
            }
        });
    }
}
