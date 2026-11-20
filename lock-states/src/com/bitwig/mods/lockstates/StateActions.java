package com.bitwig.mods.lockstates;

import bml.actions.ActionProvider;
import bml.actions.Actions;

public final class StateActions implements ActionProvider {
    @Override public void declare(Actions actions) {
        actions.action("lock_states_recall", "Recall Lock State").onRun(context -> LockStates.shortcut("recall", context.widget()));
        actions.action("lock_states_previous", "Previous Lock State").onRun(context -> LockStates.shortcut("previous", context.widget()));
        actions.action("lock_states_next", "Next Lock State").onRun(context -> LockStates.shortcut("next", context.widget()));
    }
}
