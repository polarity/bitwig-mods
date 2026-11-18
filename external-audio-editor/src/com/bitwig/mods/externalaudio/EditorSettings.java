package com.bitwig.mods.externalaudio;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

public final class EditorSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section section = preferences.section(mods, "external_audio_editor", "External Audio Editor");
        // Settings 1.0.0 has no path or text control. Use its dropdown to open our path dialog.
        preferences.dropdown("configure", "Application", "Current editor", "Configure editor...")
                .in(section).value(() -> 0, option -> {
                    if (option == 1) EditorDialog.show(null);
                });
    }
}
