package com.hibiscusmc.hmcrewards.user.data.serialize;

import com.hibiscusmc.hmcrewards.data.serialize.DnCodec;
import com.hibiscusmc.hmcrewards.data.serialize.DnReader;
import com.hibiscusmc.hmcrewards.data.serialize.DnType;
import com.hibiscusmc.hmcrewards.data.serialize.DnWriter;
import com.hibiscusmc.hmcrewards.reward.ItemRewardProvider;
import com.hibiscusmc.hmcrewards.reward.Reward;
import com.hibiscusmc.hmcrewards.reward.RewardProvider;
import com.hibiscusmc.hmcrewards.reward.RewardProviderRegistry;
import com.hibiscusmc.hmcrewards.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public final class UserCodec implements DnCodec<User> {
    private final RewardProviderRegistry rewardProviderRegistry;

    public UserCodec(final @NotNull RewardProviderRegistry rewardProviderRegistry) {
        this.rewardProviderRegistry = requireNonNull(rewardProviderRegistry, "rewardProviderRegistry");
    }

    @Override
    public @NotNull Class<User> type() {
        return User.class;
    }

    @Override
    public @NotNull User decode(final @NotNull DnReader reader) {
        reader.readObjectStart();

        UUID uuid = null;
        String name = null;
        final List<Reward> rewards = new ArrayList<>();
        boolean hasReceivedRewardsBefore = false;

        while (reader.hasMoreValuesOrEntries()) {
            String prop = reader.readName();
            if (prop.equals("uuid")) {
                uuid = UUID.fromString(reader.readStringValue());
            } else if (prop.equals("name")) {
                name = reader.readStringValue();
            } else if (prop.equals("rewards")) {
                reader.readArrayStart();
                while (reader.hasMoreValuesOrEntries()) {
                    final var readType = reader.readType();
                    if (readType == DnType.VALUE) {
                        // Read by reference (old)
                        final var ref = reader.readStringValue();
                        // todo: warn if list is empty (invalid reference)
                        rewards.addAll(rewardProviderRegistry.findByReference(ref));
                    } else if (readType == DnType.START_OBJECT) {
                        final Reward reward = decodeRewardObject(reader);
                        if (reward != null) {
                            rewards.add(reward);
                        }
                    } else {
                        reader.skipValue();
                    }
                }
                reader.readArrayEnd();
            } else if (prop.equals("hasClaimedRewardsBefore")) {
                hasReceivedRewardsBefore = reader.readBooleanValue();
            } else {
                reader.skipValue();
            }
        }

        if (uuid == null || name == null) {
            throw new IllegalStateException("Missing required properties 'uuid', 'name' or 'rewards'.");
        }

        reader.readObjectEnd();
        return User.user(uuid, name, rewards, hasReceivedRewardsBefore);
    }

    @Override
    public void encode(final @NotNull DnWriter writer, final @NotNull User value) {
        writer.writeObjectStart();
        writer.writeStringValue("uuid", value.uuid().toString());
        writer.writeStringValue("name", value.name());
        writer.writeBooleanValue("hasClaimedRewardsBefore", value.hasReceivedRewardsBefore());
        writer.writeArrayStart("rewards");
        for (final Reward reward : value.rewards()) {
            final var ref = reward.reference();
            if (ref != null) {
                writer.writeStringValue(ref);
            } else {
                encodeRewardObject(writer, reward);
            }
        }
        writer.writeArrayEnd();
        writer.writeObjectEnd();
    }

    private void encodeRewardObject(final @NotNull DnWriter writer, final @NotNull Reward reward) {
        final RewardProvider<?> provider = rewardProviderRegistry.provider(reward.type());
        if (provider == null) {
            throw new IllegalStateException("Reward provider not found: " + reward.type());
        }

        final DnCodec<Reward> codec = rewardCodec(provider, reward);
        if (codec == null) {
            throw new IllegalStateException("Reward type '" + reward.type() + "' does not support non-reference persistence.");
        }

        codec.encode(writer, reward);
    }

    private Reward decodeRewardObject(final @NotNull DnReader reader) {
        final RewardProvider<?> provider = rewardProviderRegistry.provider(ItemRewardProvider.ID);
        if (!(provider instanceof ItemRewardProvider itemRewardProvider)) {
            reader.skipValue();
            return null;
        }

        return itemRewardProvider.decode(reader);
    }

    @SuppressWarnings("unchecked")
    private @Nullable DnCodec<Reward> rewardCodec(final @NotNull RewardProvider<?> provider, final @NotNull Reward reward) {
        if (provider instanceof ItemRewardProvider codec && codec.type().isInstance(reward)) {
            return (DnCodec<Reward>) (DnCodec<?>) codec;
        }

        return null;
    }
}
