package com.bitwig.mods.scalpelstems;

import bitwig.obf.TWN; // audio event
import bitwig.obf.vFg; // timeline interval
import bml.actions.ActionContext;
import bml.actions.ActionProvider;
import bml.actions.Actions;
import com.bitwig.mods.audioanalysis.AudioSelection;
import com.bitwig.mods.audioanalysis.Dialogs;
import com.bitwig.mods.audioanalysis.Insert;
import com.bitwig.mods.audioanalysis.Jobs;
import com.bitwig.mods.audioanalysis.RuntimeActions;
import com.bitwig.mods.audioanalysis.Ui;
import com.bitwig.ramona.core.oDc; // native document object

import java.nio.file.Path;
import java.util.List;

public final class ScalpelActions implements ActionProvider {
    static final String MOD_NAME = "Scalpel Stem Separation";

    enum Mode {
        SONG("song", "Separate Song Stems", true, false,
             List.of("Vocals", "Bass", "Music", "Drums")),
        SONG_DRUMS("song-drums", "Separate Song Stems + Drum Elements", true, true,
                   List.of("Vocals", "Bass", "Music", "Kick", "Snare", "Hi-Hat", "Toms", "Ride", "Crash", "Drum Remainder")),
        DRUMS("drums", "Separate Drum Elements", false, true,
              List.of("Kick", "Snare", "Hi-Hat", "Toms", "Ride", "Crash", "Drum Remainder"));

        final String id;
        final String title;
        final boolean song;
        final boolean drums;
        final List<String> tracks;

        Mode(String id, String title, boolean song, boolean drums, List<String> tracks) {
            this.id = id;
            this.title = title;
            this.song = song;
            this.drums = drums;
            this.tracks = tracks;
        }
    }

    @Override
    public void declare(Actions actions) {
        for (Mode mode : Mode.values()) {
            actions.action("scalpel_stems_" + mode.id.replace('-', '_'), mode.title)
                .in(RuntimeActions.audioClipMenus())
                .menuOnly()
                .visibleWhen(AudioSelection::isSingleAudio)
                .onRun(context -> run(context, mode));
        }
        actions.action("scalpel_stems_enable_gpu", "Enable GPU Acceleration…")
            .in(RuntimeActions.audioClipMenus())
            .menuOnly()
            .visibleWhen(context -> canConfigureGpu(context, true))
            .onRun(context -> configureGpu(context, true));
        actions.action("scalpel_stems_disable_gpu", "Disable GPU Acceleration…")
            .in(RuntimeActions.audioClipMenus())
            .menuOnly()
            .visibleWhen(context -> canConfigureGpu(context, false))
            .onRun(context -> configureGpu(context, false));
    }

    private static boolean canConfigureGpu(ActionContext context, boolean enable) {
        if (!AudioSelection.isSingleAudio(context)) return false;
        ScalpelRuntime runtime = new ScalpelRuntime();
        return runtime.gpuSupported() && runtime.gpuEnabled() != enable;
    }

    private static void configureGpu(ActionContext context, boolean enable) {
        ScalpelRuntime runtime = new ScalpelRuntime();
        if (enable && runtime.usesCuda()) {
            CudaFolderPicker.show(runtime.cudaDialogJar(), runtime.cudaFolder(),
                folder -> confirmGpu(context, runtime, true, folder));
        } else {
            confirmGpu(context, runtime, enable, null);
        }
    }

    private static void confirmGpu(ActionContext context, ScalpelRuntime runtime, boolean enable, Path folder) {
        String title = enable ? "Enable GPU Acceleration" : "Disable GPU Acceleration";
        Dialogs.confirm(context.widget(), title, runtime.gpuDescription(enable, folder), () ->
            Jobs.start(title, control -> {
                runtime.configureGpu(enable, folder, control);
                Ui.notify(enable ? MOD_NAME + " enabled GPU acceleration" : MOD_NAME + " is using the CPU");
            })
        );
    }

    private static void run(ActionContext context, Mode mode) {
        AudioSelection clip = AudioSelection.of(context);
        if (clip == null) {
            Ui.notify(MOD_NAME + " needs one arranger audio clip playing one file");
            return;
        }
        try {
            ScalpelRuntime runtime = new ScalpelRuntime();
            List<String> downloads = runtime.setupDescription(mode);
            if (downloads.isEmpty()) {
                start(clip, mode, runtime, false);
            } else {
                Dialogs.confirm(context.widget(), "Set Up " + MOD_NAME + "…", downloads,
                    () -> start(clip, mode, runtime, true));
            }
        } catch (IllegalStateException failure) {
            Ui.notify(failure.getMessage());
        }
    }

    private static void start(AudioSelection clip, Mode mode, ScalpelRuntime runtime, boolean install) {
        if (!attached(clip)) {
            Ui.notify("The source clip is no longer available");
            return;
        }
        Jobs.start(mode.title, control -> {
            var result = runtime.separate(clip.source().toPath(), mode, install, control);
            control.phase("Adding " + result.size() + " audio tracks");
            control.onUi(() -> {
                if (!attached(clip)) throw new IllegalStateException("The source clip changed or the project closed");
                if (!Insert.audioTracks(clip, result)) throw new IllegalStateException("Bitwig did not add the stems");
                Ui.notify(MOD_NAME + " added " + result.size() + " audio tracks");
                return null;
            });
        });
    }

    private static boolean attached(AudioSelection clip) {
        if (!(clip.track() instanceof oDc track) || track.K_() == null
            || !(clip.named() instanceof oDc named) || named.L_()
            || !(clip.event() instanceof TWN event) || event.wIw() != clip.track()
            || !(clip.named() instanceof vFg interval)
            || interval.x3t() != clip.startBeat() || interval.QWG() != clip.endBeat()) {
            return false;
        }
        for (oDc owner = track; owner != null; owner = owner.K_()) { // walk to the project root
            if (owner.L_()) return false; // object has been disposed
        }
        return true;
    }
}
