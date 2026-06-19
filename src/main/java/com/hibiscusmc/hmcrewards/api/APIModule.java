package com.hibiscusmc.hmcrewards.api;

import com.hibiscusmc.hmcrewards.item.ItemMatcher;
import com.hibiscusmc.hmcrewards.hook.zmenu.ZMenuHook;
import com.hibiscusmc.hmcrewards.reward.RewardProviderRegistry;
import com.hibiscusmc.hmcrewards.user.UserManager;
import com.hibiscusmc.hmcrewards.user.data.UserDatastore;
import org.bukkit.plugin.Plugin;
import team.unnamed.inject.AbstractModule;
import team.unnamed.inject.Provides;

public class APIModule extends AbstractModule {

    @Provides
    public HMCRewardsAPI provideHMCRewardsAPI(
            UserManager userManager,
            RewardProviderRegistry rewardProviderRegistry,
            UserDatastore userDatastore,
            ItemMatcher matcher,
            ZMenuHook zMenuHook,
            Plugin plugin
    ) {
        return new HMCRewardsAPI(userManager, rewardProviderRegistry, userDatastore, matcher, zMenuHook, plugin);
    }
}
