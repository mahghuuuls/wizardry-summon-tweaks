package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Single authority for live observations, stored evidence and read-only classification. */
public final class SummonTracker {
    private static final SummonFacts.Result UNVERIFIED_ORIGIN =
            new SummonFacts.Result(null, SummonFacts.Status.UNRESOLVED, "spell_origin_unverified", false);
    private final PlayerIdentities players = new PlayerIdentities();
    private final Logger logger;
    // A failed loaded instance is retried only after reload; weak keys retain neither entities nor worlds.
    private final Map<Entity, String> failedEntities = new WeakHashMap<>();
    private final Set<String> reportedProviders = new HashSet<>();
    private Map<String, SummonProvider> providers = Collections.emptyMap();
    private boolean active;

    public SummonTracker(Logger logger) { this.logger = logger; }

    public void start(MinecraftServer server, Map<String, SummonProvider> providers) {
        players.start(server);
        failedEntities.clear();
        reportedProviders.clear();
        this.providers = new LinkedHashMap<>(providers);
        active = true;
    }

    public void stop() {
        active = false;
        providers = Collections.emptyMap();
        players.clear();
        failedEntities.clear();
        reportedProviders.clear();
    }

    public void playerJoined(UUID uuid) { if (active) players.joined(uuid); }
    public String providerState(String modId) {
        if (!active) return "not_started";
        if (!providers.containsKey(modId)) return "absent";
        return failedEntities.containsValue(modId) ? "active_with_entity_failures" : "active";
    }

    public boolean refresh(Entity entity) {
        if (!active || entity.world.isRemote) return false;
        SummonFacts.Result result = classify(entity, SummonData.peek(entity), true);
        if (result.mayPersist && result.facts != null) SummonData.remember(entity.getEntityData(), result);
        return result.affected();
    }

    public SummonFacts.Result inspect(Entity entity) {
        if (!active) return new SummonFacts.Result(null, SummonFacts.Status.UNRESOLVED, "server_not_active", false);
        return classify(entity, SummonData.peek(entity), false);
    }

    public boolean captureSpawn(Entity entity, SummonFacts facts, BooleanSupplier spawn) {
        if (!active || entity.world.isRemote || entity.isAddedToWorld()) return spawn.getAsBoolean();
        return SummonData.captureSpawn(entity.getEntityData(), facts, spawn);
    }

    private SummonFacts.Result classify(Entity entity, NBTTagCompound data, boolean recordFailure) {
        SummonFacts.Result stored = SummonData.read(data);
        if (failedEntities.containsKey(entity)) return failedObservation(stored);
        SummonFacts observation = null;
        boolean candidate = false;
        for (Map.Entry<String, SummonProvider> entry : providers.entrySet()) {
            SummonProvider provider = entry.getValue();
            try {
                if (!provider.matches(entity)) continue;
                candidate = true;
                SummonFacts current = provider.observe(entity, stored.facts);
                if (current == null) continue;
                if (observation != null) {
                    return new SummonFacts.Result(stored.facts, SummonFacts.Status.UNRESOLVED, "conflicting_origin_evidence", false);
                }
                observation = current;
            } catch (RuntimeException | LinkageError failure) {
                if (recordFailure) recordFailure(entity, entry.getKey(), failure);
                return failedObservation(stored);
            }
        }
        if (candidate && observation == null && stored.facts == null && stored.mayPersist) return UNVERIFIED_ORIGIN;
        return SummonData.reconcile(stored, observation, players::isPlayer);
    }

    private void recordFailure(Entity entity, String provider, Throwable failure) {
        failedEntities.put(entity, provider);
        if (reportedProviders.add(provider)) {
            logger.error("Summon observation failed for provider " + provider + " on " + entity.getClass().getName()
                    + " uuid=" + entity.getUniqueID() + ". This loaded instance is unresolved until reload.", failure);
        }
    }

    private SummonFacts.Result failedObservation(SummonFacts.Result stored) {
        SummonFacts.Result saved = SummonData.reconcile(stored, null, players::isPlayer);
        return new SummonFacts.Result(saved.facts, SummonFacts.Status.UNRESOLVED, "provider_observation_failed", false);
    }

}
