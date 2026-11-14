package com.gogleox.scrip.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bounded validation for optional server command aliases. */
public final class CommandAliasPolicy {
    private static final int MAX_ALIASES = 8;
    private CommandAliasPolicy() {}
    public static List<String> configuredAliases(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        Set<String> aliases = new LinkedHashSet<>();
        for (String part : raw.split(",", -1)) {
            String alias = part.trim().toLowerCase(Locale.ROOT);
            if (!alias.matches("[a-z0-9_.-]{1,32}") || alias.equals("scrip")) continue;
            aliases.add(alias);
            if (aliases.size() == MAX_ALIASES) break;
        }
        return List.copyOf(aliases);
    }
    public static List<String> withoutConflicts(Collection<String> configured, Collection<String> existingRoots) {
        Set<String> occupied = new LinkedHashSet<>();
        for (String root : existingRoots) occupied.add(root.toLowerCase(Locale.ROOT));
        List<String> result = new ArrayList<>();
        for (String alias : configured) if (occupied.add(alias.toLowerCase(Locale.ROOT))) result.add(alias);
        return List.copyOf(result);
    }
}
