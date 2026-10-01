package com.raidtracker;

import java.util.Collections;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.WorldType;

public final class WorldUtils {
    private static final EnumSet<WorldType> UNTRACKED_WORLDS = EnumSet.of(
            WorldType.BETA_WORLD,
            WorldType.DEADMAN,
            WorldType.TOURNAMENT_WORLD,
            WorldType.FRESH_START_WORLD,
            WorldType.QUEST_SPEEDRUNNING,
            WorldType.LEGACY_ONLY,
            WorldType.EOC_ONLY
    );

    private WorldUtils() {}

    /**
     * True when this world is a side mode. Leagues ({@link WorldType#SEASONAL}) stay tracked.
     * An unknown world type is not treated as untracked.
     */
    public static boolean playerOnUntrackedWorld(Client client) {
        if (client == null || client.getWorldType() == null || client.getWorldType().isEmpty()) {
            return false;
        }
        return !Collections.disjoint(client.getWorldType(), UNTRACKED_WORLDS);
    }

    /**
     * A leagues world is {@link WorldType#SEASONAL}.
     * Null means the world type is not known yet, so the log folder must not be chosen.
     */
    public static Boolean playerOnLeagueWorld(Client client) {
        if (client == null || client.getWorldType() == null || client.getWorldType().isEmpty()) {
            return null;
        }
        return client.getWorldType().contains(WorldType.SEASONAL);
    }
}
