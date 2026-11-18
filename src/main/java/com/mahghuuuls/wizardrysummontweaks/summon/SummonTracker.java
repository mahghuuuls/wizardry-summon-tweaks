package com.mahghuuuls.wizardrysummontweaks.summon;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Single authority for live observations, stored evidence and read-only classification. */
public final class SummonTracker {
    private static final SummonFacts.Result UNVERIFIED_ORIGIN =
            new SummonFacts.Result(null, SummonFacts.Status.UNRESOLVED, "spell_origin_unverified", false);
    private final PlayerIdentities players = new PlayerIdentities();
    private final Logger logger;
    // A failed loaded instance is retried only after reload; weak keys retain neither entities nor worlds.
    private final Map<Entity, Boolean> failedEntities = new WeakHashMap<>();
    private boolean failureReported;
    private SummonProvider provider;
    private String providerState = "not_started";
    private boolean active;

    public SummonTracker(Logger logger) { this.logger = logger; }

    public void start(MinecraftServer server, SummonProvider provider) {
        players.start(server);
        failedEntities.clear();
        failureReported = false;
        this.provider = provider;
        providerState = provider == null ? "absent" : "active";
        active = true;
    }

    public void stop() {
        active = false;
        provider = null;
        providerState = "not_started";
        players.clear();
        failedEntities.clear();
        failureReported = false;
    }

    public void playerJoined(UUID uuid) { if (active) players.joined(uuid); }
    public String providerState() { return providerState + (failedEntities.isEmpty() ? "" : "_with_entity_failures"); }

    public boolean refresh(Entity entity) {
        if (!active || entity.world.isRemote) return false;
        NBTTagCompound data = SummonData.peek(entity);
        boolean candidate = provider != null && provider.matches(entity);
        if (!candidate && (data == null || !data.hasKey(SummonData.KEY))) return false;
        SummonFacts.Result result = classify(entity, data, candidate, true);
        if (result.mayPersist && result.facts != null) SummonData.remember(entity.getEntityData(), result);
        return result.affected();
    }

    public SummonFacts.Result inspect(Entity entity) {
        if (!active) return new SummonFacts.Result(null, SummonFacts.Status.UNRESOLVED, "server_not_active", false);
        return classify(entity, SummonData.peek(entity), provider != null && provider.matches(entity), false);
    }

    public boolean captureSpawn(Entity entity, SummonFacts facts, BooleanSupplier spawn) {
        if (!active || entity.world.isRemote || entity.isAddedToWorld()) return spawn.getAsBoolean();
        return SummonData.captureSpawn(entity.getEntityData(), facts, spawn);
    }

    private SummonFacts.Result classify(Entity entity, NBTTagCompound data, boolean candidate, boolean recordFailure) {
        SummonFacts.Result stored = SummonData.read(data);
        if (failedEntities.containsKey(entity)) return failedObservation(stored);
        SummonFacts observation = null;
        if (candidate) {
            try {
                observation = provider.observe(entity, stored.facts);
            } catch (RuntimeException | LinkageError failure) {
                if (recordFailure) {
                    failedEntities.put(entity, Boolean.TRUE);
                    if (!failureReported) {
                        logger.error("Summon observation failed for " + entity.getClass().getName() + " uuid=" + entity.getUniqueID()
                                + "; this loaded instance is unresolved until reload. Other entities remain independent.", failure);
                        failureReported = true;
                    }
                }
                return failedObservation(stored);
            }
            if (observation == null && stored.facts == null && stored.mayPersist) return UNVERIFIED_ORIGIN;
        }
        return SummonData.reconcile(stored, observation, players::isPlayer);
    }

    private SummonFacts.Result failedObservation(SummonFacts.Result stored) {
        SummonFacts.Result saved = SummonData.reconcile(stored, null, players::isPlayer);
        return new SummonFacts.Result(saved.facts, SummonFacts.Status.UNRESOLVED, "provider_observation_failed", false);
    }

}
