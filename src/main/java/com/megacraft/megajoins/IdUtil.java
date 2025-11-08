package com.megacraft.megajoins;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class IdUtil {
    private IdUtil(){}

    public static String normalizeUuidTrimmed(String any) {
        if (any == null) return null;
        return any.replace("-", "").toLowerCase(Locale.ROOT);
    }

    public static String offlineUuidTrimmed(String name) {
        UUID u = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return u.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    /** Return the domain as the last two labels (e.g., sub.a.example.com -> example.com). */
    public static String toDomain(String host) {
        if (host == null) return "unknown";
        String h = host.toLowerCase(Locale.ROOT);
        String[] parts = h.split("\\.");
        if (parts.length < 2) return h;
        return parts[parts.length-2] + "." + parts[parts.length-1];
    }
}
