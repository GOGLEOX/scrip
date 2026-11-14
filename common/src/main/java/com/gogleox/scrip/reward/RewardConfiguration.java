package com.gogleox.scrip.reward;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Durable server reward policy. Amounts are whole currency units and zero disables a source. */
public record RewardConfiguration(Map<RewardSource, Long> amounts, long starterGrant,
                                  StarterGrantMode starterGrantMode, int activePlaytimeMinutes,
                                  long revision) {
    public RewardConfiguration {
        Objects.requireNonNull(amounts, "amounts"); Objects.requireNonNull(starterGrantMode, "starterGrantMode");
        if (starterGrant < 0 || activePlaytimeMinutes < 1 || activePlaytimeMinutes > 1440 || revision < 0) throw new IllegalArgumentException("Invalid reward configuration");
        EnumMap<RewardSource, Long> copy = new EnumMap<>(RewardSource.class);
        for (RewardSource source : RewardSource.values()) { long amount = amounts.getOrDefault(source, 0L); if (amount < 0) throw new IllegalArgumentException("Reward amount cannot be negative"); copy.put(source, amount); }
        amounts = Map.copyOf(copy);
    }
    public static RewardConfiguration defaults() { return new RewardConfiguration(Map.of(), 0, StarterGrantMode.FIRST_JOIN, 30, 0); }
    public long amountFor(RewardSource source) { return amounts.getOrDefault(source, 0L); }
    public boolean enabled(RewardSource source) { return amountFor(source) > 0; }
}
