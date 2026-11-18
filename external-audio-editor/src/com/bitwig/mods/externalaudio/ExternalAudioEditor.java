package com.bitwig.mods.externalaudio;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import bitwig.obf.BVS; // a clip/pattern whose content is a stream of timeline events (6.1.7)
import bitwig.obf.DyF; // an audio timeline event containing sample regions
import bitwig.obf.aJf; // common Arranger/Launcher Clip Inspector
import bitwig.obf.aMo; // audio-event Inspector (also used for MIDI notes)
import bitwig.obf.dBz; // native push button
import bitwig.obf.dXL; // native widget group
import bitwig.obf.poA; // audio sample region
import bml.api.Bml;
import bml.inspector.Blocks;
import bml.inspector.BuiltBlock;
import bml.inspector.Context;
import bml.inspector.InspectorProvider;
import bml.notifications.Notifications;

public final class ExternalAudioEditor implements InspectorProvider {
    private static final Bml.Mod MOD = Bml.mod("external-audio-editor");
    private static final AtomicBoolean STARTING = new AtomicBoolean();

    @Override
    public void declare(Blocks blocks) {
        blocks.onBlockBuilt(ExternalAudioEditor::addButton);
    }

    private static void addButton(BuiltBlock block) {
        if (!block.bitwigBlock() || !(block.widget() instanceof dXL body)) return;
        if (block.panel() instanceof aMo panel) {
            if (!panel.UIe().BBg() || !block.title().equals(String.valueOf(panel.XzC().getValue()))) return;
        } else if (block.panel() instanceof aJf panel) {
            if (block.context() != Context.ARRANGER_CLIP && block.context() != Context.LAUNCHER_CLIP) return;
            if (!block.title().equals(String.valueOf(panel.XzC().getValue()))) return;
        } else return;

        // Resolve only Bitwig's in-memory source references here, with no filesystem access.
        if (!hasAudio(selection(block.panel()))) return;
        dBz button = dBz.beI("Edit externally", () -> openSelection(block.panel()));
        button.zzi("Open the original sample file in your external audio editor");
        body.IvP(button);
    }

    private static List<?> selection(Object panel) {
        if (panel instanceof aMo events) return events.UIe().bK_();
        if (panel instanceof aJf clips) return clips.GKF().bK_();
        return List.of();
    }

    private static boolean hasAudio(List<?> selected) {
        if (selected.size() != 1) return false;
        Object item = selected.getFirst();
        if (item instanceof poA || item instanceof DyF) return true;
        if (item instanceof BVS clip && clip.ZRB() != null) {
            try (Stream<?> events = clip.ZRB().zzi()) {
                return events.anyMatch(event -> event instanceof DyF);
            }
        }
        return false;
    }

    private static void openSelection(Object panel) {
        try {
            // Re-read on click: the Inspector may have been reused since its widgets were built.
            List<?> selected = selection(panel);
            if (selected.size() != 1) throw new IOException("Select one audio clip or audio event.");
            java.util.ArrayList<File> paths = new java.util.ArrayList<>();
            collect(selected.getFirst(), paths);
            // Bitwig model access stays on its UI thread; filesystem work and process creation do not.
            Thread.ofPlatform().daemon(true).name("external-audio-source").start(() -> {
                try {
                    SourceFiles sources = new SourceFiles();
                    for (File file : paths) sources.add(file);
                    File source = sources.single();
                    if (editor().isBlank()) EditorDialog.show(source);
                    else launch(source);
                } catch (IOException | RuntimeException error) {
                    fail(error.getMessage(), error);
                }
            });
        } catch (IOException | RuntimeException | LinkageError error) {
            fail("Could not read the selected audio: " + error.getMessage(), error);
        }
    }

    private static void collect(Object item, List<File> files) throws IOException {
        if (item instanceof poA region) {
            var sample = region.GKF();
            files.add(sample == null ? null : sample.tWW());
        } else if (item instanceof DyF event) {
            try (Stream<?> regions = event.vws()) {
                for (var iterator = regions.iterator(); iterator.hasNext();) collect(iterator.next(), files);
            }
        } else if (item instanceof BVS clip && clip.ZRB() != null) {
            try (Stream<?> events = clip.ZRB().zzi()) {
                for (var iterator = events.iterator(); iterator.hasNext();) collect(iterator.next(), files);
            }
        } else {
            throw new IOException("This selection does not contain a supported audio sample.");
        }
    }

    static String editor() { return MOD.settings().get("editor-path", ""); }
    static void setEditor(String path) { MOD.settings().put("editor-path", path); }
    static java.nio.file.Path dialogJar() { return MOD.resource("editor-dialog"); }

    static void launch(File source) {
        if (!STARTING.compareAndSet(false, true)) return;
        String application = editor();
        Thread.ofPlatform().daemon(true).name("external-audio-editor").start(() -> {
            try {
                List<String> command = EditorCommand.of(application, source, System.getProperty("os.name", ""));
                Process process = EditorCommand.start(command);
                // A launcher may report an immediate error. Never wait for the editing session to end.
                if (process.waitFor(1200, TimeUnit.MILLISECONDS) && process.exitValue() != 0) {
                    throw new IOException("The editor exited with code " + process.exitValue() + ". Check its application path.");
                }
                MOD.log("Opened sample in external editor: " + source);
            } catch (IOException | RuntimeException error) {
                fail("Could not open the external editor: " + error.getMessage(), error);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                STARTING.set(false);
            }
        });
    }

    static void fail(String message, Throwable error) {
        MOD.log(message, error);
        Notifications.toast("External Audio Editor: " + message);
    }
}
