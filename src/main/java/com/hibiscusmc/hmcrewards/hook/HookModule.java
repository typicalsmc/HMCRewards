package com.hibiscusmc.hmcrewards.hook;

import com.hibiscusmc.hmcrewards.hook.placeholderapi.PlaceholderAPIHook;
import com.hibiscusmc.hmcrewards.hook.zmenu.ZMenuHook;
import com.hibiscusmc.hmcrewards.util.BukkitAbstractModule;

public final class HookModule extends BukkitAbstractModule {
    @Override
    protected void configure() {
        bind(ZMenuHook.class).singleton();
        bindServices().to(PlaceholderAPIHook.class);
        bindServices().to(ZMenuHook.class);
        bindListeners().to(ZMenuHook.class);
    }
}
