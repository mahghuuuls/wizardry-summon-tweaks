package com.mahghuuuls.wizardrysummontweaks.config;

/** Active values belong to one server opening, never to a physical client's file. */
public final class ServerRules {
    public static final ServerRules DEFAULTS = new ServerRules(true, true, true);
    public final boolean suppressSummonDrops;
    public final boolean protectPlayers;
    public final boolean protectSummons;

    public ServerRules(boolean suppressSummonDrops, boolean protectPlayers, boolean protectSummons) {
        this.suppressSummonDrops = suppressSummonDrops;
        this.protectPlayers = protectPlayers;
        this.protectSummons = protectSummons;
    }
}
