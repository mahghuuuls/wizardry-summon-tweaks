package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.server.MinecraftServer;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-session player confirmations. No entity references or combat-time disk lookup. */
final class PlayerIdentities {
    private final Set<UUID> known = new HashSet<>();

    public void start(MinecraftServer server) {
        known.clear();
        for (String file : server.getPlayerList().getAvailablePlayerDat()) {
            try { known.add(UUID.fromString(file)); }
            catch (IllegalArgumentException ignored) { /* An unrelated filename is not player evidence. */ }
        }
        server.getPlayerList().getPlayers().forEach(player -> known.add(player.getUniqueID()));
    }

    public void joined(UUID player) { known.add(player); }
    public boolean isPlayer(UUID uuid) { return uuid != null && known.contains(uuid); }
    public void clear() { known.clear(); }
}
