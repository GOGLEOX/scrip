package com.gogleox.scrip.network;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/** Server-supplied wallet presentation metadata. Icon resources must be supplied by an installed or server resource pack. */
public record WalletBranding(String displayName, Optional<String> iconTexture) {
    private static final String PREFIX = "WALLET_BRAND:";
    public WalletBranding {
        displayName = validateName(displayName);
        iconTexture = iconTexture == null ? Optional.empty() : iconTexture.filter(WalletBranding::validResource);
    }
    public static WalletBranding serverConfigured() {
        String name = System.getProperty("scrip.wallet.name", "$crip");
        String icon = System.getProperty("scrip.wallet.icon", "");
        return new WalletBranding(name, icon.isBlank() ? Optional.empty() : Optional.of(icon));
    }
    public String wireValue() {
        String name = Base64.getUrlEncoder().withoutPadding().encodeToString(displayName.getBytes(StandardCharsets.UTF_8));
        String icon = iconTexture.map(value -> Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8))).orElse("");
        return PREFIX + name + ":" + icon;
    }
    public static Optional<WalletBranding> fromWireValue(String value) {
        if (value == null || !value.startsWith(PREFIX)) return Optional.empty();
        String[] parts = value.substring(PREFIX.length()).split(":", -1);
        if (parts.length != 2) return Optional.empty();
        try {
            String name = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            Optional<String> icon = parts[1].isEmpty() ? Optional.empty() : Optional.of(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            return Optional.of(new WalletBranding(name, icon));
        } catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }
    private static String validateName(String value) {
        if (value == null) return "$crip";
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 32 || trimmed.codePoints().anyMatch(Character::isISOControl)) return "$crip";
        return trimmed;
    }
    private static boolean validResource(String value) { return value.matches("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,160}"); }
}
