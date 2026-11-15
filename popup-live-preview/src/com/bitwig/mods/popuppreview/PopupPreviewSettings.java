package com.bitwig.mods.popuppreview;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

public final class PopupPreviewSettings implements PreferenceProvider {
    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section browser = preferences.section(mods, "popup_live_preview", "Pop-up Browser");
        preferences.dropdown("live_preview", "Live Preview",
                "Remember last choice", "Open with preview on", "Open with preview off")
            .in(browser)
            .value(PopupLivePreview::mode, PopupLivePreview::setMode);
    }
}
