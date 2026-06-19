package com.hibiscusmc.hmcrewards.hook.zmenu;

import com.hibiscusmc.hmcrewards.reward.Reward;
import com.hibiscusmc.hmcrewards.reward.RewardProvider;
import com.hibiscusmc.hmcrewards.reward.RewardProviderRegistry;
import com.hibiscusmc.hmcrewards.user.User;
import com.hibiscusmc.hmcrewards.user.UserManager;
import com.hibiscusmc.hmcrewards.util.Service;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import team.unnamed.inject.Inject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ZMenuHook implements Service, Listener {
    @Inject private Plugin plugin;
    @Inject private UserManager userManager;
    @Inject private RewardProviderRegistry rewardProviderRegistry;

    private Object inventoriesPlayer;
    private Method hasSavedInventoryMethod;
    private final Map<UUID, List<PendingTask>> pendingTasks = new ConcurrentHashMap<>();

    @Override
    public void start() {
        if (!Bukkit.getPluginManager().isPluginEnabled("zMenu")) return;

        try {
            final Class<?> inventoriesPlayerClass = Class.forName(
                    "fr.maxlego08.menu.api.players.inventory.InventoriesPlayer");
            final RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(inventoriesPlayerClass);
            if (rsp == null) {
                plugin.getLogger().warning("zMenu found but InventoriesPlayer service not registered");
                return;
            }
            inventoriesPlayer = rsp.getProvider();
            hasSavedInventoryMethod = inventoriesPlayerClass.getMethod("hasSavedInventory", UUID.class);
            plugin.getLogger().info("zMenu hook enabled - clear-inventory detection active");
        } catch (final Exception e) {
            plugin.getLogger().warning("Failed to initialize zMenu hook: " + e.getMessage());
        }
    }

    public boolean isInClearInventory(@NotNull Player player) {
        if (inventoriesPlayer == null || hasSavedInventoryMethod == null) return false;
        try {
            return (boolean) hasSavedInventoryMethod.invoke(inventoriesPlayer, player.getUniqueId());
        } catch (final Exception e) {
            return false;
        }
    }

    public void markBlocked(@NotNull Player player) {
        queueItemRewardRetry(player);
    }

    public @NotNull CompletableFuture<Void> runAfterClearInventory(
            final @NotNull Player player,
            final @NotNull Runnable task,
            final boolean closeInventory
    ) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        if (Bukkit.isPrimaryThread()) {
            runAfterClearInventoryOnMain(player, task, closeInventory, future);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> runAfterClearInventoryOnMain(player, task, closeInventory, future));
        }
        return future;
    }

    private void runAfterClearInventoryOnMain(
            final @NotNull Player player,
            final @NotNull Runnable task,
            final boolean closeInventory,
            final @NotNull CompletableFuture<Void> future
    ) {
        if (!player.isOnline()) {
            future.completeExceptionally(new IllegalStateException("Player is offline"));
            return;
        }

        if (!isInClearInventory(player)) {
            runTask(task, future);
            return;
        }

        pendingTasks.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).add(new PendingTask(task, future));
        if (closeInventory) {
            player.closeInventory();
        }
    }

    private void queueItemRewardRetry(@NotNull Player player) {
        final UUID uuid = player.getUniqueId();
        final List<PendingTask> tasks = pendingTasks.computeIfAbsent(uuid, ignored -> new ArrayList<>());
        for (final PendingTask task : tasks) {
            if (task.rewardRetry) return;
        }
        tasks.add(PendingTask.rewardRetry(() -> retryGiveItems(player)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(final @NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!pendingTasks.containsKey(player.getUniqueId())) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || isInClearInventory(player)) return;
            drainPendingTasks(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
        pendingTasks.remove(event.getPlayer().getUniqueId());
    }

    private void drainPendingTasks(@NotNull Player player) {
        final List<PendingTask> tasks = pendingTasks.remove(player.getUniqueId());
        if (tasks == null || tasks.isEmpty()) return;

        for (final PendingTask task : tasks) {
            runTask(task.runnable, task.future);
        }
    }

    private void runTask(@NotNull Runnable task, @NotNull CompletableFuture<Void> future) {
        try {
            task.run();
            future.complete(null);
        } catch (final Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void retryGiveItems(@NotNull Player player) {
        final User user = userManager.getCached(player);
        if (user == null) return;

        final var rewards = user.rewards();
        boolean anyGiven = false;

        final Iterator<Reward> it = rewards.iterator();
        while (it.hasNext()) {
            final Reward reward = it.next();
            if (!"item".equals(reward.type())) continue;

            final RewardProvider provider = rewardProviderRegistry.provider(reward.type());
            if (provider == null) continue;

            final RewardProvider.GiveResult result = provider.give(player, reward);
            if (result == RewardProvider.GiveResult.SUCCESS) {
                it.remove();
                anyGiven = true;
            }
        }

        if (anyGiven) {
            userManager.saveAsync(user);
        }
    }

    private record PendingTask(@NotNull Runnable runnable, @NotNull CompletableFuture<Void> future, boolean rewardRetry) {
        private PendingTask(@NotNull Runnable runnable, @NotNull CompletableFuture<Void> future) {
            this(runnable, future, false);
        }

        private static PendingTask rewardRetry(@NotNull Runnable runnable) {
            return new PendingTask(runnable, new CompletableFuture<>(), true);
        }
    }
}
