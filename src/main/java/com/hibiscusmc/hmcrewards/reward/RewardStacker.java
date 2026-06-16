package com.hibiscusmc.hmcrewards.reward;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class RewardStacker {
    private RewardStacker() {
    }

    public static void addStacking(
            final @NotNull List<Reward> rewards,
            final @NotNull Collection<? extends Reward> additions,
            final @NotNull RewardProviderRegistry registry
    ) {
        for (final Reward addition : additions) {
            final RewardProvider<?> provider = registry.provider(addition.type());
            if (provider == null) {
                throw new IllegalArgumentException("Reward provider not found: " + addition.type());
            }

            boolean stacked = false;
            for (int i = 0; i < rewards.size(); i++) {
                final Reward existing = rewards.get(i);
                if (!existing.type().equals(addition.type())) {
                    continue;
                }

                final Reward combined = stack(provider, existing, addition);
                if (combined != null) {
                    rewards.set(i, combined);
                    stacked = true;
                    break;
                }
            }

            if (!stacked) {
                rewards.add(addition);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Reward stack(final @NotNull RewardProvider provider, final @NotNull Reward existing, final @NotNull Reward addition) {
        return provider.stack(existing, addition);
    }
}
